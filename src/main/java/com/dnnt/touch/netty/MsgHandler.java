package com.dnnt.touch.netty;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.dnnt.touch.domain.IMMsg;
import com.dnnt.touch.domain.User;
import com.dnnt.touch.mapper.MsgMapper;
import com.dnnt.touch.mapper.UserMapper;
import com.dnnt.touch.protobuf.ChatProto;
import com.dnnt.touch.util.Constant;
import io.netty.channel.*;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class MsgHandler extends ChannelDuplexHandler {

    private UserMapper userMapper;
    private MsgMapper msgMapper;

    private static Map<Long,ChannelHandlerContext> ctxMap = new ConcurrentHashMap<>();

    private List<ChatProto.ChatMsg> msgList = new LinkedList<>();

    private Set<Long> friendsId;

    private long userId = 0;


    public MsgHandler(UserMapper userMapper,MsgMapper msgMapper){
        this.userMapper = userMapper;
        this.msgMapper = msgMapper;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ChatProto.ChatMsg){

            ChatProto.ChatMsg chatMsg = (ChatProto.ChatMsg) msg;

            System.out.println(chatMsg.toString());

            switch (chatMsg.getType()){
                case Constant.TYPE_MSG:
                    handleMsg(ctx,chatMsg);
                    break;
                case Constant.TYPE_CONNECTED:
                    handleConnected(ctx,chatMsg);
                    break;
                case Constant.TYPE_ACK:
                    handleAck(chatMsg);
                    break;
                case Constant.TYPE_ADD_FRIEND:
                    handleAddFriend(ctx,chatMsg);
                    break;
                case Constant.TYPE_FRIEND_AGREE:
                    handleFriendAgree(ctx,chatMsg);
                    break;
            }
        }
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        super.close(ctx, promise);
    }

    private void handleConnected(ChannelHandlerContext ctx, ChatProto.ChatMsg chatMsg){
        try {
            DecodedJWT decodedJWT = JWT.require(Algorithm.HMAC256(Constant.TOKEN_KEY))
                    .build()
                    .verify(chatMsg.getMsg());
            long id = decodedJWT.getClaim(User.ID).asLong();
            if (id == chatMsg.getFrom()){
                userId = id;
                ctxMap.put(userId,ctx);
                friendsId = userMapper.getUserFriends(userId);
                List<IMMsg> imMsgs = msgMapper.getMsg(userId);
                for (IMMsg msg: imMsgs) {
                    ctx.write(ChatProto.ChatMsg.newBuilder()
                            .setFrom(msg.getFromId())
                            .setTo(msg.getToId())
                            .setMsg(msg.getMsg())
                            .setTime(msg.getTime())
                            .setType(msg.getType())
                            .build());
                }
                ctx.flush();
                msgMapper.deleteMsg(chatMsg.getFrom());
            }else {
                ctx.close();
            }
        }catch (Exception e){
            ctx.close();
        }

    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        ctx.executor().schedule(() -> {
            if (userId == 0){
                ctx.close();
            }
        },6,TimeUnit.SECONDS);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("MsgHandler : inactive");
        ctxMap.remove(userId);
        super.channelInactive(ctx);
    }

    private void handleFriendAgree(ChannelHandlerContext ctx, ChatProto.ChatMsg chatMsg){
        userMapper.insertFriend(chatMsg.getTo(),chatMsg.getFrom());
        friendsId.add(chatMsg.getTo());
        ChannelHandlerContext toCtx = ctxMap.get(chatMsg.getTo());
        if (toCtx != null){
            toCtx.executor().execute(() -> {
                Set<Long> friends = ((MsgHandler)toCtx.pipeline().get(Constant.MSG_HANDLER)).friendsId;
                friends.add(chatMsg.getFrom());
            });
        }
        handleChatMsg(ctx,chatMsg);
    }

    private void handleAddFriend(ChannelHandlerContext ctx, ChatProto.ChatMsg chatMsg){
        String nameOrPhone = chatMsg.getMsg();
        User user = null;
        if(nameOrPhone.matches("\\d{11}")){
            user = userMapper.selectByPhone(nameOrPhone);
        }else if (!nameOrPhone.matches("\\d.*")){
            user = userMapper.selectByName(nameOrPhone);
        }
        if (user == null){
            //给发送者返回消息(用户不存在)
            ctx.writeAndFlush(ChatProto.ChatMsg.newBuilder()
                    .setType(Constant.TYPE_USER_NOT_EXIST)
                    .setSeq(chatMsg.getSeq())
                    .build());
        }else {
            if (friendsId.contains(user.getId())){
                ctx.writeAndFlush(ChatProto.ChatMsg.newBuilder()
                        .setType(Constant.TYPE_USER_ALREADY_ADD)
                        .setSeq(chatMsg.getSeq())
                        .build());
                return;
            }
            User fromUser = userMapper.selectById(chatMsg.getFrom());
            if (user.getId() == fromUser.getId()){
                return;
            }
            //查看好申请是否已在IMMsg中
            IMMsg imMsg = msgMapper.selectAddFriendMsg(chatMsg.getFrom(),user.getId(),Constant.TYPE_ADD_FRIEND);
            if (imMsg != null){
                return;
            }
            chatMsg = chatMsg.toBuilder()
                    //用';'隔开userName和headUrl
                    .setMsg(fromUser.getUserName() + Constant.SPLIT_CHAR + fromUser.getHeadUrl())
                    .setTo(user.getId())
                    .build();

            handleChatMsg(ctx,chatMsg);
        }
    }

    private void handleMsg(ChannelHandlerContext ctx, ChatProto.ChatMsg chatMsg){
        if (friendsId.contains(chatMsg.getTo())){
            handleChatMsg(ctx,chatMsg);
        }else {
            sendACK(ctx,chatMsg);
            ctx.writeAndFlush(ChatProto.ChatMsg.newBuilder()
                    .setSeq(chatMsg.getSeq())
                    .setType(Constant.TYPE_SEND_FAIL));
        }
    }

    private void handleChatMsg(ChannelHandlerContext ctx, ChatProto.ChatMsg chatMsg){
        //设置消息时间,客户端无法保证时间的正确性
        chatMsg = chatMsg
                .toBuilder()
                .setTime(System.currentTimeMillis())
                .build();

        //给发送者返回ACK
        sendACK(ctx,chatMsg);

        ChannelHandlerContext toCtx = ctxMap.get(chatMsg.getTo());

        sendMsg(toCtx,chatMsg);
    }

    private void sendACK(ChannelHandlerContext ctx, ChatProto.ChatMsg chatMsg){
        ctx.writeAndFlush(ChatProto.ChatMsg.newBuilder()
                .setType(Constant.TYPE_ACK)
                .setSeq(chatMsg.getSeq())
                .build());
    }

    private void sendMsg(ChannelHandlerContext toCtx, ChatProto.ChatMsg chatMsg){
        if (toCtx != null){
            //将消息发送给接收者，在接收者的EventLoop中操作,将msg存到接收者handler的msgList中
            toCtx.executor().execute(() -> {
                List<ChatProto.ChatMsg> toList = ((MsgHandler)toCtx.pipeline().get(Constant.MSG_HANDLER)).msgList;
                toList.add(chatMsg);
                toCtx.executor().execute(() -> toCtx.writeAndFlush(chatMsg));
            });

            //定时器，接收者无ACK返回或超时返回时，将msg存到数据库并从msgList中移除
            toCtx.executor().schedule(() -> {
                List<ChatProto.ChatMsg> toList = ((MsgHandler)toCtx.pipeline().get(Constant.MSG_HANDLER)).msgList;
                for (int i = 0; i < toList.size(); i++) {
                    ChatProto.ChatMsg temp = toList.get(i);
                    if (temp.equals(chatMsg)){
                        toList.remove(i);
                        saveMsg(temp);
                        break;
                    }
                }
            },10000, TimeUnit.MILLISECONDS);
        }else {
            saveMsg(chatMsg);
        }
    }

    private void saveMsg(ChatProto.ChatMsg chatMsg){
        IMMsg imMsg = new IMMsg(chatMsg.getFrom(),chatMsg.getTo(),chatMsg.getMsg(),chatMsg.getTime(),chatMsg.getType());
        msgMapper.insertMsg(imMsg);
    }

    private void handleAck(ChatProto.ChatMsg chatMsg){
        //接收者返回ACK，则将msg从接收者的handler的msgList中移除，在接收者的EventLoop中操作
        ChannelHandlerContext toCtx = ctxMap.get(chatMsg.getTo());
        if (toCtx != null){
            toCtx.executor().execute(() -> removeReceivedMsg(chatMsg,toCtx));
        }
    }

    private void removeReceivedMsg(ChatProto.ChatMsg msg, ChannelHandlerContext toCtx){
        List<ChatProto.ChatMsg> toList = ((MsgHandler)toCtx.pipeline().get(Constant.MSG_HANDLER)).msgList;
        for (int i = 0; i < toList.size(); i++) {
            ChatProto.ChatMsg temp = toList.get(i);
            //除type以外都相等
            if (temp.getFrom() == msg.getFrom() && temp.getTo() == msg.getTo() && temp.getSeq() == msg.getSeq()){
                toList.remove(i);
                break;
            }
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent){
            System.out.println("idle");
            System.out.println(((IdleStateEvent) evt).isFirst());
            IdleStateEvent event = (IdleStateEvent)evt;
            if (event.state() == IdleState.READER_IDLE && !event.isFirst()){
                ctxMap.remove(userId);
                ctx.close();
                System.out.println("overtime remove");
            }
        }else {
            super.userEventTriggered(ctx,evt);
        }
    }
}

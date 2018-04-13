package com.dnnt.touch.netty;

import com.dnnt.touch.domain.IMMsg;
import com.dnnt.touch.domain.User;
import com.dnnt.touch.mapper.MsgMapper;
import com.dnnt.touch.mapper.UserMapper;
import com.dnnt.touch.protobuf.ChatProto;
import com.dnnt.touch.util.Constant;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class MsgHandler extends ChannelDuplexHandler {

    private UserMapper userMapper;
    private MsgMapper msgMapper;

    private static Map<Long,ChannelHandlerContext> ctxMap = new ConcurrentHashMap<>();

    private List<ChatProto.ChatMsg> msgList = new LinkedList<>();

    private List<Long> friendsId = new ArrayList<>();

    private long userId;


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
        userId = chatMsg.getFrom();
        friendsId = userMapper.getUserFriends(userId);
        ctxMap.put(userId,ctx);
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
            toCtx.executor().submit(() -> {
                List<Long> friends = ((MsgHandler)toCtx.pipeline().get(Constant.MSG_HANDLER)).friendsId;
                friends.add(chatMsg.getFrom());
            });
        }
        handleMsg(ctx,chatMsg);
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
            for (long item:friendsId) {
                if (item == user.getId()){
                    ctx.writeAndFlush(ChatProto.ChatMsg.newBuilder()
                            .setType(Constant.TYPE_USER_ALREADY_ADD)
                            .setSeq(chatMsg.getSeq())
                            .build());
                    return;
                }
            }
            User fromUser = userMapper.selectById(chatMsg.getFrom());
            if (user.getId() == fromUser.getId()){
                return;
            }
            IMMsg imMsg = msgMapper.selectAddFriendMsg(chatMsg.getFrom(),user.getId(),Constant.TYPE_ADD_FRIEND);
            if (imMsg != null){
                return;
            }
            chatMsg = chatMsg.toBuilder()
                    //用';'隔开userName和headUrl
                    .setMsg(fromUser.getUserName() + Constant.SPLIT_CHAR + fromUser.getHeadUrl())
                    .setTo(user.getId())
                    .build();

            handleMsg(ctx,chatMsg);
        }
    }

    private void handleMsg(ChannelHandlerContext ctx, ChatProto.ChatMsg chatMsg){
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
            toCtx.executor().submit(() -> {
                List<ChatProto.ChatMsg> toList = ((MsgHandler)toCtx.pipeline().get(Constant.MSG_HANDLER)).msgList;
                toList.add(chatMsg);
                toCtx.executor().submit(() -> toCtx.writeAndFlush(chatMsg));
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
            toCtx.executor().submit(() -> removeReceivedMsg(chatMsg,toCtx));
        }
    }

    private void removeReceivedMsg(ChatProto.ChatMsg msg, ChannelHandlerContext toCtx){
        List<ChatProto.ChatMsg> toList = ((MsgHandler)toCtx.pipeline().get(Constant.MSG_HANDLER)).msgList;
        for (int i = 0; i < toList.size(); i++) {
            ChatProto.ChatMsg temp = toList.get(i);
            //除type以外都相等
            if (temp.getSeq() == msg.getSeq() && temp.getTime() == msg.getTime()
                    && temp.getFrom() == msg.getFrom() && temp.getTo() == msg.getTo()
                    && temp.getMsg().equals(msg.getMsg())){
                toList.remove(i);
                break;
            }
        }
    }
}

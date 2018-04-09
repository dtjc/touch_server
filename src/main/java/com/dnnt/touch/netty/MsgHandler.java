package com.dnnt.touch.netty;

import com.dnnt.touch.protobuf.ChatProto;
import com.dnnt.touch.util.Constant;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class MsgHandler extends ChannelDuplexHandler {

    private static Map<Long,ChannelHandlerContext> map = new ConcurrentHashMap<>();

    private List<ChatProto.ChatMsg> msgList = new LinkedList<>();

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
                    map.put(chatMsg.getFrom(),ctx);
                    //TODO get from database?
                    break;
                case Constant.TYPE_ACK:
                    handleAck(chatMsg);
                    break;
                case Constant.TYPE_ADD_FRIEND:
                    //TODO
                    break;
            }
        }
    }

    private void handleMsg(ChannelHandlerContext ctx, ChatProto.ChatMsg chatMsg){
        //设置消息时间
        chatMsg = chatMsg
                .toBuilder()
                .setTime(System.currentTimeMillis())
                .build();

        //给发送者返回ACK
        ctx.writeAndFlush(ChatProto.ChatMsg.newBuilder()
                .setType(Constant.TYPE_ACK)
                .setSeq(chatMsg.getSeq())
                .build());

        ChannelHandlerContext toCtx = map.get(chatMsg.getTo());
        if (toCtx != null){

            //将消息发送给接收者，在接收者的EventLoop中操作
            if (toCtx.executor().inEventLoop()){
                toCtx.writeAndFlush(chatMsg);
            }else {
                ChatProto.ChatMsg finalChatMsg = chatMsg;
                toCtx.executor().submit(() -> toCtx.writeAndFlush(finalChatMsg));
            }

            //将msg存到发送者handler的msgList中
            msgList.add(chatMsg);
            //seq由客户端保证唯一性
            int seq = chatMsg.getSeq();
            //定时器，接收者无ACK返回或超时返回时，将msg存到数据库并从msgList中移除
            ctx.executor().schedule(() -> {
                for (int i = 0; i < msgList.size(); i++) {
                    ChatProto.ChatMsg temp = msgList.get(i);
                    if (temp.getSeq() > seq){
                        break;
                    }
                    if (seq == temp.getSeq()){
                        msgList.remove(i);
                        //TODO save into database
                    }
                }
            },10000, TimeUnit.MILLISECONDS);
//                    MsgHandler mh = (MsgHandler) toCtx.pipeline().get(Constant.MSG_HANDLER);
//                    mh.msgList.add()
//                    System.out.println(toCtx.handler());
        }else {
            //TODO save into database
        }

    }

    private void handleAck(ChatProto.ChatMsg chatMsg){
        //接收者返回ACK，则将msg从发送者的handler的msgList中移除，在发送者的EventLoop中操作
        ChannelHandlerContext fromCtx = map.get(chatMsg.getFrom());
        if (fromCtx != null){
            if (fromCtx.executor().inEventLoop()){
                removeReceivedMsg(chatMsg,fromCtx);
            }else {
                fromCtx.executor().submit(() -> removeReceivedMsg(chatMsg,fromCtx));
            }
        }
    }

    private void removeReceivedMsg(ChatProto.ChatMsg msg, ChannelHandlerContext fromCtx){
        List<ChatProto.ChatMsg> fromList = ((MsgHandler)fromCtx.pipeline().get(Constant.MSG_HANDLER)).msgList;
        for (int i = 0; i < fromList.size(); i++) {
            ChatProto.ChatMsg temp = fromList.get(i);
            if (temp.getSeq() > msg.getSeq()){
                break;
            }
            if (temp.getSeq() == msg.getSeq() && temp.getTime() == msg.getTime()){
                fromList.remove(i);
            }
        }
    }
}

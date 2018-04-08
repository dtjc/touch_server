package com.dnnt.touch.netty;

import com.dnnt.touch.protobuf.ChatProto;
import com.dnnt.touch.util.Constant;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MsgHandler extends ChannelDuplexHandler {

    private static Map<Long,ChannelHandlerContext> map = new ConcurrentHashMap<>();


    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ChatProto.ChatMsg){
            ChatProto.ChatMsg chatMsg = ((ChatProto.ChatMsg) msg)
                    .toBuilder()
                    .setTime(System.currentTimeMillis())
                    .build();
            System.out.println(chatMsg.toString());
            int type = chatMsg.getType();
            if (type == Constant.TYPE_MSG){

                ctx.writeAndFlush(ChatProto.ChatMsg.newBuilder()
                        .setType(Constant.TYPE_ACK)
                        .setSeq(chatMsg.getSeq())
                        .build());

                //TODO 这里不保证消息能100%发送到
                ChannelHandlerContext toCtx = map.get(chatMsg.getTo());
                if (toCtx != null){
                    if (toCtx.executor().inEventLoop()){
                        toCtx.writeAndFlush(msg);
                    }else {
                        toCtx.executor().submit(() -> toCtx.writeAndFlush(msg));
                    }
                }

            }else if (type == Constant.TYPE_CONNECTED){
                map.put(chatMsg.getFrom(),ctx);
            }
        }

    }
}

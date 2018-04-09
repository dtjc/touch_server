package com.dnnt.touch.netty;


import com.dnnt.touch.protobuf.ChatProto;
import com.dnnt.touch.util.Constant;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

@Component
public class NettyStartUp implements InitializingBean {
    public static final int PORT = 9999;
    @Override
    public void afterPropertiesSet(){
        new Thread(() -> {
            NioEventLoopGroup bossGroup = new NioEventLoopGroup();
            NioEventLoopGroup workerGroup = new NioEventLoopGroup();
            try {
                ServerBootstrap serverBootstrap = new ServerBootstrap();
                serverBootstrap.group(bossGroup,workerGroup)
                        .channel(NioServerSocketChannel.class)
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel socketChannel) throws Exception {
                                ChannelPipeline pl = socketChannel.pipeline();
                                pl.addLast(new ProtobufVarint32FrameDecoder());
                                pl.addLast(new ProtobufDecoder(ChatProto.ChatMsg.getDefaultInstance()));
                                pl.addLast(new ProtobufVarint32LengthFieldPrepender());
                                pl.addLast(new ProtobufEncoder());
                                pl.addLast(Constant.MSG_HANDLER,new MsgHandler());
                            }
                        }).childOption(ChannelOption.SO_KEEPALIVE,true);
                ChannelFuture cf = serverBootstrap.bind(PORT).sync();
                cf.channel().closeFuture().sync();
            }catch (Exception e){
                e.printStackTrace();
            }finally {
                bossGroup.shutdownGracefully();
                workerGroup.shutdownGracefully();
            }
        }).start();


    }
}

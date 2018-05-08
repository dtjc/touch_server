package com.dnnt.touch.netty;


import com.dnnt.touch.mapper.MsgMapper;
import com.dnnt.touch.mapper.UserMapper;
import com.dnnt.touch.protobuf.ChatProto;
import com.dnnt.touch.util.Constant;
import com.dnnt.touch.util.SecureUtilKt;
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
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleStateHandler;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.io.File;

@Component
public class NettyStartUp implements InitializingBean {
    public static final int PORT = 9999;

    private UserMapper userMapper;
    private MsgMapper msgMapper;
    private SSLContext sslContext;

    @Autowired
    public NettyStartUp(UserMapper userMapper,MsgMapper msgMapper){
        this.userMapper = userMapper;
        this.msgMapper = msgMapper;
    }

    @Override
    public void afterPropertiesSet(){
        sslContext = SecureUtilKt.getSSLContext();

        new Thread(() -> {
            NioEventLoopGroup bossGroup = new NioEventLoopGroup();
            NioEventLoopGroup workerGroup = new NioEventLoopGroup();
            try {
                ServerBootstrap serverBootstrap = new ServerBootstrap();
                serverBootstrap.group(bossGroup,workerGroup)
                        .channel(NioServerSocketChannel.class)
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel socketChannel){
                                ChannelPipeline pl = socketChannel.pipeline();
                                SSLEngine sslEngine = sslContext.createSSLEngine();
                                sslEngine.setUseClientMode(false);
                                pl.addLast(new SslHandler(sslEngine));
                                pl.addLast(new ProtobufVarint32FrameDecoder());
                                pl.addLast(new ProtobufDecoder(ChatProto.ChatMsg.getDefaultInstance()));
                                pl.addLast(new ProtobufVarint32LengthFieldPrepender());
                                pl.addLast(new ProtobufEncoder());
                                pl.addLast(new IdleStateHandler(6,0,0));
                                pl.addLast(Constant.MSG_HANDLER,new MsgHandler(userMapper,msgMapper));
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

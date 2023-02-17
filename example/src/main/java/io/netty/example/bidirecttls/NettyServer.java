package io.netty.example.bidirecttls;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

import java.io.InputStream;

public class NettyServer {
    private static final int PORT = Integer.parseInt(System.getProperty("port", "8443"));

    public static void main(String[] args) throws Exception{
        InputStream serverCrt = NettyClient.class.getResourceAsStream("server.crt");
        InputStream caCrt = NettyClient.class.getResourceAsStream("ca.crt");
        InputStream serverKey = NettyClient.class.getResourceAsStream("pkcs8_server.key");
        SslContext sslContext= SslContextBuilder
                .forServer(serverCrt, serverKey)
                .trustManager(caCrt)
                .clientAuth(ClientAuth.REQUIRE)
                .build();
        EventLoopGroup parentGroup=new NioEventLoopGroup(1);
        EventLoopGroup childGroup=new NioEventLoopGroup();
        try {
            ServerBootstrap bootstrap=new ServerBootstrap();
            bootstrap.group(parentGroup,childGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG,128)
                    .childHandler(new ServerChannelInitializer(sslContext));
            ChannelFuture future=bootstrap.bind(PORT).sync();
            System.out.println("netty server start done...");
            future.channel().closeFuture().sync();
        }catch (Exception e){
            System.out.println("netty server start error...");
        }finally {
            parentGroup.shutdownGracefully();
            childGroup.shutdownGracefully();
        }
    }
}

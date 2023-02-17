package io.netty.example.bidirecttls;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

import java.io.InputStream;

public class NettyClient {

    private static final int PORT = Integer.parseInt(System.getProperty("port", "8443"));
    private static final String HOST = System.getProperty("host", "127.0.0.1");

    public static void main(String[] args) throws Exception{

        InputStream clientCrt = NettyClient.class.getResourceAsStream("client.crt");
        InputStream caCrt = NettyClient.class.getResourceAsStream("ca.crt");
        InputStream clientKey = NettyClient.class.getResourceAsStream("pkcs8_client.key");

        SslContext sslContext = SslContextBuilder.forClient()
                .keyManager(clientCrt, clientKey)
                .trustManager(caCrt)
                .build();

        EventLoopGroup workGroup=new NioEventLoopGroup();
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(workGroup)
                    .channel(NioSocketChannel.class)
                    .handler(new ClientChannelInitializer(sslContext));
            ChannelFuture future = bootstrap.connect(HOST, PORT).sync();
            System.out.println("netty client start done ....");
            future.channel().closeFuture().sync();
        } catch (Exception e) {
            System.err.println("netty client start error ...");
        } finally {
            workGroup.shutdownGracefully();
        }
    }
}

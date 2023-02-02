package io.netty.example.http.par;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;

import java.net.URI;
import java.util.concurrent.TimeUnit;

public class HttpParClient {
    static final String URL = System.getProperty("url", "http://127.0.0.1:8080/");

    public static void main(String[] args) throws Exception{
        URI uri = new URI(URL);
        String scheme = uri.getScheme() == null? "http" : uri.getScheme();
        String host = uri.getHost() == null? "127.0.0.1" : uri.getHost();
        int port = uri.getPort();
        if (port == -1) {
            if ("http".equalsIgnoreCase(scheme)) {
                port = 80;
            } else if ("https".equalsIgnoreCase(scheme)) {
                port = 443;
            }
        }

        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            System.err.println("Only HTTP(S) is supported.");
            return;
        }

        EventLoopGroup group = new NioEventLoopGroup();

        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline p = ch.pipeline();
                            p.addLast(new HttpClientCodec());
                            p.addLast(new HttpParClientHandler());
                        }
                    });
            Channel channel = b.connect(host, port).sync().channel();
            for (int i = 0; i < 3; i++) {
                TimeUnit.SECONDS.sleep(1);
                sendRequest(uri, host, channel, i);
            }
            channel.closeFuture().sync();
        } finally {
            group.shutdownGracefully();
        }
    }

    private static void sendRequest(URI uri, String host, Channel ch, int count) {
        System.err.println("request: -------- " + count);
//        HttpRequest request = new DefaultHttpRequest(
//                HttpVersion.HTTP_1_1, HttpMethod.GET, uri.getRawPath() + "?count=" + count);
//        request.headers().set(HttpHeaderNames.HOST, host);
//        ch.write(request);
//        ch.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);


        DefaultFullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET, uri.getRawPath() + "?count=" + count);
        request.headers().set(HttpHeaderNames.HOST, host);
        ch.writeAndFlush(request);
    }
}

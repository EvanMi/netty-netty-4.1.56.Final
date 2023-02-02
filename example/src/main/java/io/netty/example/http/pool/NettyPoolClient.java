package io.netty.example.http.pool;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.pool.AbstractChannelPoolMap;
import io.netty.channel.pool.ChannelPoolMap;
import io.netty.channel.pool.FixedChannelPool;
import io.netty.channel.pool.SimpleChannelPool;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class NettyPoolClient {
    //event loop group
    final EventLoopGroup group = new NioEventLoopGroup();
    //bootstrap
    final Bootstrap bootstrap = new Bootstrap();
    //pool map
    //其中的key一般为指定的url地址，对每个url地址建立一个pool
    ChannelPoolMap<String, SimpleChannelPool> poolMap;

    //初始化构建
    public void build() throws Exception {
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                //链接超时时长
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(new LoggingHandler(LogLevel.INFO));
        poolMap = new UrlPoolMap(bootstrap);


    }


    public static class UrlPoolMap extends AbstractChannelPoolMap<String, SimpleChannelPool> {

        private Bootstrap bootstrap;

        public UrlPoolMap(Bootstrap bootstrap) {
            this.bootstrap = bootstrap;
        }

        @Override
        protected SimpleChannelPool newPool(String key) {
            InetSocketAddress inetAddress = null;
            boolean isSSL = false;
            try {
                URI uri = new URI(key);
                if (Objects.isNull(uri)) {
                    return null;
                }
                String host = uri.getHost();
                isSSL = uri.getScheme().equals("https");
                InetAddress address = InetAddress.getByName(host);
                if (!host.equalsIgnoreCase(address.getHostAddress())) {
                    inetAddress = new InetSocketAddress(address, isSSL ? 443 : 80);
                } else {
                    int port = uri.getPort();
                    inetAddress = InetSocketAddress.createUnresolved(host, port);
                }
            } catch (Throwable e) {
                //请求地址不合法
            }

            if (Objects.nonNull(inetAddress)){
                return new FixedChannelPool(bootstrap.remoteAddress(inetAddress),new NettyChannelPoolHandler(isSSL),2);

            }

            return null;
        }
    }


    public static void main(String[] args) throws Exception{
        NettyPoolClient client = new NettyPoolClient();
        client.build();

        String url = "http://127.0.0.1:8080";

        for (int i = 0; i < 10; i++) {
            HttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, url);
            SimpleChannelPool pool = client.poolMap.get(url);
            Future<Channel> f = pool.acquire();

            f.addListener((FutureListener<Channel>) f1 -> {
                if (f1.isSuccess()) {
                    Channel ch = f1.getNow();
                    ch.writeAndFlush(request);
                    NettyClientHandler nettyClientHandler = ch.pipeline().get(NettyClientHandler.class);
                    if (null == nettyClientHandler) {
                        return;
                    }
                    CompletableFuture<String> cf = nettyClientHandler.getCf();
                    cf.thenAccept(res -> {
                                if (null != res) {
                                    System.out.println(res);
                                }
                            })
                            .thenRun(() -> pool.release(ch)
                    );
//                    //刷出数据的同时进行监听刷出的结果
//                    channelFuture.addListener(new ChannelFutureListener() {
//                        @Override
//                        public void operationComplete(ChannelFuture future) throws Exception {
//                            //这里中刷出成功，并不代表客户接收成功，刷出数据成功默认代表已完成发送
//                            //System.out.println("http netty client刷出数据结果为：" + future.isSuccess());
//                        }
//                    });
                }
            });
        }
    }
}

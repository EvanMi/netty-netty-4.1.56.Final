package io.netty.example.http.pool;

import io.netty.channel.Channel;
import io.netty.channel.pool.ChannelPoolHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.timeout.IdleStateHandler;

public class NettyChannelPoolHandler implements ChannelPoolHandler {

    private boolean isSSL;

    public NettyChannelPoolHandler(boolean isSSL) {
        this.isSSL = isSSL;
    }

    //这个方法会在将channel放到队列以后才会执行，所以在这里进行清理操作是不安全的
    //目前还没有想到这里做什么操作合适，发点通知？做点统计信息？
    @Override
    public void channelReleased(Channel ch) throws Exception {
        //released
        System.out.println("channelReleased. Channel ID:"+ch.id());
    }

    //先调用该方法，然后才会返回chanel，所以做一些重置的操作是合适的
    @Override
    public void channelAcquired(Channel ch) throws Exception {
        //acquired
        NettyClientHandler nettyClientHandler = ch.pipeline().get(NettyClientHandler.class);
        nettyClientHandler.resetCf();
        System.out.println("channelAcquired. Channel ID:"+ch.id());
    }

    //该方法会被嵌入到initChannel方法中去执行，所以在这里进行pipeline的初始化动作
    @Override
    public void channelCreated(Channel ch) throws Exception {
        SocketChannel socketChannel = (SocketChannel) ch;
        //socketChannel.config().setKeepAlive(true);
        //socketChannel.config().setTcpNoDelay(true);

        if (isSSL) {
            SslContext context = SslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .build();
            socketChannel.pipeline().addLast(context.newHandler(socketChannel.alloc()));
        }

        socketChannel.pipeline()
                .addLast(new HttpClientCodec())
                .addLast(new HttpObjectAggregator(1024 * 1024 * 10))
                .addLast(new HttpContentDecompressor())
                .addLast(new NettyClientHandler());
        //空闲链接检测
        socketChannel.pipeline().addFirst(new IdleStateHandler(5, 5, 0));
    }
}

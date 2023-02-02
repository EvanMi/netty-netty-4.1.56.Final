package io.netty.example.http.pool;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.util.CharsetUtil;

import java.util.concurrent.CompletableFuture;

public class NettyClientHandler extends SimpleChannelInboundHandler<FullHttpResponse> {

    private volatile CompletableFuture<String> cf = new CompletableFuture<>();


    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg) throws Exception {
        ByteBuf content = msg.content();
        cf.complete(content.toString(CharsetUtil.UTF_8));
    }

    public void resetCf() {
        this.cf = new CompletableFuture<>();
    }

    public CompletableFuture<String> getCf() {
        return cf;
    }
}

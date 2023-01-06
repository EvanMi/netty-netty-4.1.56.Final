package io.netty.example.file;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;

public class FileClient {
    public static void main(String[] args) throws Exception{
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap client = new Bootstrap();
            client.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<NioSocketChannel>() {
                        @Override
                        protected void initChannel(NioSocketChannel ch) throws Exception {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new StringEncoder(CharsetUtil.UTF_8),
                                    new LineBasedFrameDecoder(8192, false, true),
                                    new StringDecoder(CharsetUtil.UTF_8),
                                    new EchoHandler()
                            );
                        }
                    });
            ChannelFuture localhost = client.connect("localhost", 8023).sync();
            localhost.channel().writeAndFlush("input/abc.txt\n");
            localhost.channel().closeFuture().sync();
            System.out.print("客户端完成~");
        } finally {
            group.shutdownGracefully();
        }
    }


    public static class EchoHandler extends SimpleChannelInboundHandler<String> {
        Integer totalLength = Integer.MAX_VALUE;
        Integer curLength = 0;

        @Override
        protected void channelRead0(final ChannelHandlerContext ctx, String msg) throws Exception {
            System.out.print(msg);
            if (msg.startsWith("OK:")) {
                String s = msg.split(" ")[1];
                this.totalLength = Integer.parseInt(s.substring(0, s.length() - 1));
            } else if(!msg.startsWith("ERR:") && !msg.startsWith("HELLO:")){
                int len = msg.getBytes().length;
                curLength += len;
            }
            if (curLength >= totalLength) {
                ctx.channel().eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        ctx.channel().close();
                    }
                });
            }

        }
    }
}

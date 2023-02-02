package io.netty.example.http.par;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutorGroup;

import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.netty.handler.codec.http.HttpResponseStatus.CONTINUE;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class HttpParServerHandler extends SimpleChannelInboundHandler<Object> {
    static class MyTask implements Runnable {
        public String data;
        public HttpRequest request;
        public ChannelHandlerContext ctx;
        public DefaultPromise<MyTask> promise;
        Deque<DefaultPromise<MyTask>> queue;
        private final AtomicBoolean state;

        public MyTask(HttpRequest request, ChannelHandlerContext ctx, DefaultPromise<MyTask> promise, Deque<DefaultPromise<MyTask>> queue, AtomicBoolean state) {
            this.request = request;
            this.ctx = ctx;
            this.promise = promise;
            this.queue = queue;
            this.state = state;
        }

        @Override
        public void run() {
            Random random = new Random();
            int next = random.nextInt(10);
            try {
                TimeUnit.SECONDS.sleep(next);
            } catch (Exception e) {
                //
            }
            QueryStringDecoder queryStringDecoder = new QueryStringDecoder(request.uri());
            Map<String, List<String>> params = queryStringDecoder.parameters();
            final String paramStr = params.toString();
            this.data = paramStr;
            promise.setSuccess(this);

            if (!queue.isEmpty() && queue.getLast() == this.promise && state.compareAndSet(false, true)) {
                while (!queue.isEmpty() && queue.getLast().isSuccess()) {
                    DefaultPromise<MyTask> newPro = queue.pollLast();
                    MyTask newTask = newPro.getNow();

                    boolean keepAlive = HttpUtil.isKeepAlive(newTask.request);

                    // Build the response object.
                    FullHttpResponse response = new DefaultFullHttpResponse(
                            HTTP_1_1, OK, Unpooled.copiedBuffer(newTask.data, CharsetUtil.UTF_8));

                    response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");

                    if (keepAlive) {
                        // Add 'Content-Length' header only for a keep-alive connection.
                        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
                        // Add keep alive header as per:
                        // - https://www.w3.org/Protocols/HTTP/1.1/draft-ietf-http-v11-spec-01.html#Connection
                        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
                    }

                    // Write the response.
                    newTask.ctx.writeAndFlush(response);

                    if (!keepAlive) {
                        newTask.ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
                    }
                }
                state.set(false);
            }
        }
    }

    static final EventExecutorGroup group = new DefaultEventExecutorGroup(16);

    private final AtomicBoolean state = new AtomicBoolean(false);

    private HttpRequest request;

    private final Deque<DefaultPromise<MyTask>> queue = new ConcurrentLinkedDeque<>();


    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            //处理http请求
            HttpRequest request = this.request = (HttpRequest) msg;

            //处理100 continue请求
            if (HttpUtil.is100ContinueExpected(request)) {
                send100Continue(ctx);
                return;
            }
        }

        if (msg instanceof LastHttpContent) {
            final DefaultPromise<MyTask> promise = new DefaultPromise<>(group.next());
            queue.addFirst(promise);
            group.submit(new MyTask(this.request, ctx, promise, queue, state));
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }

    private static void send100Continue(ChannelHandlerContext ctx) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, CONTINUE, Unpooled.EMPTY_BUFFER);
        ctx.write(response);
    }
}

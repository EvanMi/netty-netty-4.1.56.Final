/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.example.objectecho;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.internal.ThreadLocalRandom;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.netty.channel.ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE;

/**
 * Handler implementation for the object echo client.  It initiates the
 * ping-pong traffic between the object echo client and server by sending the
 * first message to the server.
 */
public class ObjectEchoClientHandler extends ChannelInboundHandlerAdapter {

    private final List<Integer> firstMessage;

    /**
     * Creates a client-side handler.
     */
    public ObjectEchoClientHandler() {
        firstMessage = new ArrayList<>(ObjectEchoClient.SIZE);
        for (int i = 0; i < ObjectEchoClient.SIZE; i ++) {
            firstMessage.add(Integer.valueOf(i));
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        // Send the first message if this handler is a client-side handler.
        ChannelFuture future = ctx.writeAndFlush(firstMessage);
        // 可以学到的是netty中默认提供的listener
        future.addListener(FIRE_EXCEPTION_ON_FAILURE); // Let object serialisation exceptions propagate.
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws InterruptedException {//对父类中方法上的异常进行收缩
        // Echo back the received object to the server.
        TimeUnit.SECONDS.sleep(2);
        assert msg instanceof List;
        List<Integer> msgLst = (List<Integer>) msg;
        int size = msgLst.size();
        int i = ThreadLocalRandom.current().nextInt(size);
        msgLst.set(i, msgLst.get(i) + (msgLst.get(i) % 2 == 0 ? 1 : -1));
        ctx.write(msgLst);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        //使用这种方式来发送消息，那么所有的业务逻辑需要在channelRead中同步处理
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}

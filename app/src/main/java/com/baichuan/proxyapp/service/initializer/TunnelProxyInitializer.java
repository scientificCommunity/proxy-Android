package com.baichuan.proxyapp.service.initializer;

import com.baichuan.proxyapp.service.listener.ForwardListener;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.string.StringEncoder;
import lombok.extern.slf4j.Slf4j;

/**
 * @author kun
 * @date 2020-06-19 9:34
 * 不解码返回数据
 */
@Slf4j
public class TunnelProxyInitializer extends ChannelInitializer<Channel> {
    private final ForwardListener listener;

    public TunnelProxyInitializer(ForwardListener listener) {
        this.listener = listener;
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
        ch.pipeline()
                .addLast(new StringEncoder())
//                .addLast(new RequestBoCodec())
                .addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                        listener.writeAndFlush(msg);
                    }

                    @Override
                    public void channelUnregistered(ChannelHandlerContext ctx0) throws Exception {
                        ctx0.channel().close();
                        listener.channelUnregistered();
                    }

                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx0, Throwable cause) throws Exception {
                        log.error("==================================连接服务端异常==================================");
                        ctx0.channel().close();
                        listener.exceptionCaught(cause);
                        super.exceptionCaught(ctx0,cause);
                    }
                });
    }
}

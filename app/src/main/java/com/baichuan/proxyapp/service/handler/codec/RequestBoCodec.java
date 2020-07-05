package com.baichuan.proxyapp.service.handler.codec;

import com.alibaba.fastjson.JSONObject;
import com.baichuan.proxyapp.domain.RequestBO;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;
import lombok.extern.slf4j.Slf4j;

import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.List;

/**
 * @author kun
 * @date 2020-06-26 17:13
 */
@Slf4j
public class RequestBoCodec extends ByteToMessageCodec<RequestBO> {

    private final Charset charset;

    public RequestBoCodec(Charset charset) {
        this.charset = charset;
    }

    public RequestBoCodec() {
        this.charset = Charset.defaultCharset();
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, RequestBO msg, ByteBuf out) throws Exception {
        if (msg == null) {
            return;
        }

        String json = JSONObject.toJSONString(msg);
        int length = json.length();
        out = ByteBufUtil.encodeString(ctx.alloc(), CharBuffer.wrap(length + json), charset);
        ctx.channel().pipeline().remove(this);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {

    }
}

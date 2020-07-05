package com.baichuan.proxyapp.service;

import android.annotation.SuppressLint;
import android.os.Build;
import androidx.annotation.RequiresApi;
import com.alibaba.fastjson.JSONObject;
import com.baichuan.proxyapp.constants.RequestTimeStatus;
import com.baichuan.proxyapp.domain.ProxyConfig;
import com.baichuan.proxyapp.domain.RequestBO;
import com.baichuan.proxyapp.service.initializer.TunnelProxyInitializer;
import com.baichuan.proxyapp.service.listener.ForwardListener;
import com.baichuan.proxyapp.utils.RequestUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author kun
 * @date 2020-06-11 17:34
 */
@Slf4j
public class ServerHandler extends ChannelInboundHandlerAdapter {
    private final EventLoopGroup eventExecutors;

    private ChannelFuture remoteServerCf;

    private static final String CONNECT_METHOD = "connect";

    private static final HttpResponseStatus CONNECTION_ESTABLISHED = new HttpResponseStatus(200, "Connection established");

    private RequestBO requestBO;

    private boolean proxyConnected2Server;

    private int status;

    private final ProxyConfig proxyConfig;

    public ServerHandler(EventLoopGroup eventExecutors, ProxyConfig proxyConfig) {
        this.eventExecutors = eventExecutors;
        this.proxyConfig = proxyConfig;
    }

    @SuppressLint("NewApi")
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        boolean isHttp = false;
        if (msg instanceof HttpRequest) {
            HttpRequest req = (HttpRequest) msg;

            if (status == RequestTimeStatus.FIRST) {
                status = RequestTimeStatus.NOT_FIRST;
                this.requestBO = RequestUtils.getRequestBO(req);

                log.debug("新的代理请求进入：host:{},port{}", requestBO.getHost(), requestBO.getPort());

                if (CONNECT_METHOD.equalsIgnoreCase(req.method().name())) {
                    HttpResponse resp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, CONNECTION_ESTABLISHED);
                    ctx.writeAndFlush(resp);
                    ctx.pipeline().remove(HttpServerCodec.class);
                    initConnection2Server(new ForwardListener(ctx));
                    ReferenceCountUtil.release(msg);
                    return;
                }
            }
        } else if (msg instanceof HttpContent) {
            isHttp = true;
        }
        forward(msg, isHttp);
    }

    private static final ReentrantLock WRITE_TO_REMOTE_LOCK = new ReentrantLock();

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("=======================================读取客户端消息异常================================");
        ctx.channel().close();
        super.exceptionCaught(ctx, cause);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void initConnection2Server(ForwardListener listener) {
        synchronized (unSendReqs) {
            Bootstrap proxy2ServerEd = new Bootstrap();
            proxy2ServerEd.group(eventExecutors)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.SO_KEEPALIVE, Boolean.TRUE)
                    .handler(new TunnelProxyInitializer(listener));

            remoteServerCf = proxy2ServerEd.connect("182.61.174.247", 9005);
//            remoteServerCf = proxy2ServerEd.connect("192.168.0.103", 9005);

            remoteServerCf.addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {

                    String json = JSONObject.toJSONString(requestBO);
                    int length = json.length();
                    ChannelFuture channelFuture = future.channel().writeAndFlush(length + json);

                    channelFuture.addListener(future1 -> {
                        if (channelFuture.isSuccess()) {
                            channelFuture.channel().pipeline().remove(StringEncoder.class);
                            synchronized (unSendReqs) {
                                unSendReqs.forEach(o -> remoteServerCf.channel().writeAndFlush(o));
                                unSendReqs.clear();
                                proxyConnected2Server = true;
                            }
                        }
                    });
                } else {
                    log.error("proxy2server connection failed,host:{},port:{}", this.requestBO.getHost(), this.requestBO.getPort(), future.cause());
                    unSendReqs.forEach(ReferenceCountUtil::release);
                    unSendReqs.clear();
                    future.channel().close();
                }
            });
        }
    }

    public void forward(Object clientMsg, boolean isHttp) {
        if (remoteServerCf == null && !(clientMsg instanceof HttpRequest) && isHttp) {
            return;
        }
        synchronized (unSendReqs) {
            if (proxyConnected2Server) {
                remoteServerCf.channel().writeAndFlush(clientMsg);
            } else {
                unSendReqs.add(clientMsg);
            }
        }
    }

    /**
     * 顺序存取
     */
    private final List<Object> unSendReqs = new LinkedList<>();
}

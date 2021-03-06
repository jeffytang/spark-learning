package com.twq.network;

import com.twq.network.client.TransportClient;
import com.twq.network.client.TransportClientFactory;
import com.twq.network.client.TransportResponseHandler;
import com.twq.network.config.TransportConf;
import com.twq.network.protocol.MessageDecoder;
import com.twq.network.protocol.MessageEncoder;
import com.twq.network.protocol.TransportFrameDecoder;
import com.twq.network.server.RpcHandler;
import com.twq.network.server.TransportChannelHandler;
import com.twq.network.server.TransportRequestHandler;
import com.twq.network.server.TransportServer;
import com.twq.network.util.NettyUtils;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

public class TransportContext {
    private static final Logger logger = LoggerFactory.getLogger(TransportContext.class);

    private static final MessageEncoder ENCODER = MessageEncoder.INSTANCE;
    private static final MessageDecoder DECODER = MessageDecoder.INSTANCE;

    private final TransportConf conf;
    private final RpcHandler rpcHandler;

    private final boolean closeIdleConnections;

    public TransportContext(TransportConf conf, RpcHandler rpcHandler) {
        this(conf, rpcHandler, false);
    }

    public TransportContext(
            TransportConf conf,
            RpcHandler rpcHandler,
            boolean closeIdleConnections) {
        this.conf = conf;
        this.rpcHandler = rpcHandler;
        this.closeIdleConnections = closeIdleConnections;
    }

    public TransportClientFactory createClientFactory() {
        return new TransportClientFactory(this);
    }

    public TransportServer createServer() {
        return createServer(0);
    }

    public TransportServer createServer(int port) {
        return new TransportServer(this, null, port);
    }

    public TransportServer createServer(String host, int port) {
        return new TransportServer(this, host, port);
    }

    public TransportChannelHandler initializePipeline(SocketChannel channel) {
        return initializePipeline(channel, rpcHandler);
    }

    public TransportChannelHandler initializePipeline(
            SocketChannel channel,
            RpcHandler channelRpcHandler) {

        try {
            TransportChannelHandler channelHandler = createChannelHandler(channel, channelRpcHandler);
            channel.pipeline()
                    .addLast("encoder", ENCODER)
                    .addLast(TransportFrameDecoder.HANDLER_NAME, NettyUtils.createFrameDecoder())
                    .addLast("decoder", DECODER)
                    .addLast("idleStateHandler",
                            new IdleStateHandler(0, 0,
                                    conf.connectionTimeoutMs() / 1000))
                    .addLast("handler", channelHandler);
            return channelHandler;
        } catch (RuntimeException e) {
            logger.error("Error while initializing Netty pipeline", e);
            throw e;
        }

    }


    private TransportChannelHandler createChannelHandler(
            SocketChannel channel,
            RpcHandler channelRpcHandler) {
        TransportResponseHandler responseHandler = new TransportResponseHandler(channel);
        TransportClient client = new TransportClient(channel, responseHandler);
        TransportRequestHandler requestHandler = new TransportRequestHandler(channel,
                    client, channelRpcHandler, conf.maxChunksBeingTransferred());
        return new TransportChannelHandler(client, requestHandler, responseHandler,
                conf.connectionTimeoutMs(), closeIdleConnections);
    }

    public TransportConf getConf() {
        return conf;
    }
}

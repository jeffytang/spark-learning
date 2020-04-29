package com.twq.network.server;

import com.google.common.base.Throwables;
import com.twq.network.buffer.NioManagedBuffer;
import com.twq.network.client.RpcResponseCallback;
import com.twq.network.client.TransportClient;
import com.twq.network.protocol.*;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.nio.ByteBuffer;

/**
 * A handler that processes requests from clients and writes chunk data back. Each handler is
 * attached to a single Netty channel, and keeps track of which streams have been fetched via this
 * channel, in order to clean them up if the channel is terminated (see #channelUnregistered).
 *
 * The messages should have been processed by the pipeline setup by {@link TransportServer}.
 */
public class TransportRequestHandler extends MessageHandler<RequestMessage> {
    private static final Logger logger = LoggerFactory.getLogger(TransportRequestHandler.class);

    /** The Netty channel that this handler is associated with. */
    private final Channel channel;
    /** Client on the same channel allowing us to talk back to the requester. */
    private final TransportClient reverseClient;

    /** Handles all RPC messages. */
    private final RpcHandler rpcHandler;

    public TransportRequestHandler(
            Channel channel,
            TransportClient reverseClient,
            RpcHandler rpcHandler) {
        this.channel = channel;
        this.reverseClient = reverseClient;
        this.rpcHandler = rpcHandler;
    }
    @Override
    public void handle(RequestMessage message) throws Exception {
        if (message instanceof RpcRequest) {
            processRpcRequest((RpcRequest) message);
        }
    }

    private void processRpcRequest(final RpcRequest req) {
        try {
            rpcHandler.receive(reverseClient, req.body().nioByteBuffer(), new RpcResponseCallback() {
                @Override
                public void onSuccess(ByteBuffer response) {
                    respond(new RpcResponse(req.requestId, new NioManagedBuffer(response)));
                }

                @Override
                public void onFailure(Throwable e) {
                    respond(new RpcFailure(req.requestId, Throwables.getStackTraceAsString(e)));
                }
            });
        } catch (Exception e) {
            logger.error("Error while invoking RpcHandler#receive() on RPC id " + req.requestId, e);
        }
    }

    private ChannelFuture respond(Encodable result) {
        SocketAddress remoteAddress = channel.remoteAddress();
        return channel.writeAndFlush(result).addListener(future -> {
            if (future.isSuccess()) {
                logger.trace("Sent result {} to client {}", result, remoteAddress);
            } else {
                logger.error(String.format("Error sending result %s to %s; closing connection",
                        result, remoteAddress), future.cause());
                channel.close();
            }
        });
    }

    @Override
    public void channelActive() {
        rpcHandler.channelActive(reverseClient);
    }

    @Override
    public void exceptionCaught(Throwable cause) {
        rpcHandler.exceptionCaught(cause, reverseClient);
    }

    @Override
    public void channelInactive() {
        rpcHandler.channelInactive(reverseClient);
    }
}
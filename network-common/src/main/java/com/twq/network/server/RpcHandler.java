package com.twq.network.server;

import com.twq.network.client.RpcResponseCallback;
import com.twq.network.client.StreamCallbackWithID;
import com.twq.network.client.TransportClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

/**
 * Handler for sendRPC() messages sent by {@link com.twq.network.client.TransportClient}s.
 */
public abstract class RpcHandler {
    private static final RpcResponseCallback ONE_WAY_CALLBACK = new OneWayRpcCallback();

    /**
     * Receive a single RPC message. Any exception thrown while in this method will be sent back to
     * the client in string form as a standard RPC failure.
     *
     * Neither this method nor #receiveStream will be called in parallel for a single
     * TransportClient (i.e., channel).
     *
     * @param client A channel client which enables the handler to make requests back to the sender
     *               of this RPC. This will always be the exact same object for a particular channel.
     * @param message The serialized bytes of the RPC.
     * @param callback Callback which should be invoked exactly once upon success or failure of the
     *                 RPC.
     */
    public abstract void receive(
            TransportClient client,
            ByteBuffer message,
            RpcResponseCallback callback);

    /**
     * Receives an RPC message that does not expect a reply. The default implementation will
     * call "{@link #receive(TransportClient, ByteBuffer, RpcResponseCallback)}" and log a warning if
     * any of the callback methods are called.
     *
     * @param client A channel client which enables the handler to make requests back to the sender
     *               of this RPC. This will always be the exact same object for a particular channel.
     * @param message The serialized bytes of the RPC.
     */
    public void receive(TransportClient client, ByteBuffer message) {
        receive(client, message, ONE_WAY_CALLBACK);
    }

    /**
     * Receive a single RPC message which includes data that is to be received as a stream. Any
     * exception thrown while in this method will be sent back to the client in string form as a
     * standard RPC failure.
     *
     * Neither this method nor #receive will be called in parallel for a single TransportClient
     * (i.e., channel).
     *
     * An error while reading data from the stream
     * ({@link com.twq.network.client.StreamCallback#onData(String, ByteBuffer)})
     * will fail the entire channel.  A failure in "post-processing" the stream in
     * {@link com.twq.network.client.StreamCallback#onComplete(String)} will result in an
     * rpcFailure, but the channel will remain active.
     *
     * @param client A channel client which enables the handler to make requests back to the sender
     *               of this RPC. This will always be the exact same object for a particular channel.
     * @param messageHeader The serialized bytes of the header portion of the RPC.  This is in meant
     *                      to be relatively small, and will be buffered entirely in memory, to
     *                      facilitate how the streaming portion should be received.
     * @param callback Callback which should be invoked exactly once upon success or failure of the
     *                 RPC.
     * @return a StreamCallback for handling the accompanying streaming data
     */
    public StreamCallbackWithID receiveStream(
            TransportClient client,
            ByteBuffer messageHeader,
            RpcResponseCallback callback) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the StreamManager which contains the state about which streams are currently being
     * fetched by a TransportClient.
     */
    public abstract StreamManager getStreamManager();

    /**
     * Invoked when the channel associated with the given client is active.
     */
    public void channelActive(TransportClient client) { }

    /**
     * Invoked when the channel associated with the given client is inactive.
     * No further requests will come from this client.
     */
    public void channelInactive(TransportClient client) { }

    public void exceptionCaught(Throwable cause, TransportClient client) { }

    private static class OneWayRpcCallback implements RpcResponseCallback {

        private static final Logger logger = LoggerFactory.getLogger(OneWayRpcCallback.class);

        @Override
        public void onSuccess(ByteBuffer response) {
            logger.warn("Response provided for one-way RPC.");
        }

        @Override
        public void onFailure(Throwable e) {
            logger.error("Error response provided for one-way RPC.", e);
        }

    }
}

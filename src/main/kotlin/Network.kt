/**
 * Copyright 2018 Fetch.AI Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fetch.oef.sdk.kotlin

import com.google.protobuf.AbstractMessage
import fetch.oef.pb.AgentOuterClass
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.lang.Exception
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.AsynchronousCloseException
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.ClosedChannelException
import java.nio.channels.CompletionHandler
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


private object AsyncIOConnectHandler : CompletionHandler<Void?, CancellableContinuation<Unit>> {
    override fun completed(result: Void?, attachment: CancellableContinuation<Unit>?) {
        attachment?.resume(Unit)
    }

    override fun failed(exc: Throwable, attachment: CancellableContinuation<Unit>?) {
        if (exc is AsynchronousCloseException && attachment?.isCancelled == true) return
        attachment?.resumeWithException(exc)
    }
}

private object AsyncIOHandler : CompletionHandler<Int, CancellableContinuation<Int>> {
    override fun completed(result: Int, attachment: CancellableContinuation<Int>?) {
        attachment?.resume(result)
    }

    override fun failed(exc: Throwable, attachment: CancellableContinuation<Int>?) {
        if (exc is AsynchronousCloseException && attachment?.isCancelled == true) return
        attachment?.resumeWithException(exc)
    }
}


private suspend fun AsynchronousSocketChannel.aConnect(address: SocketAddress) = suspendCancellableCoroutine<Unit> {
    connect(address, it, AsyncIOConnectHandler)
    it.invokeOnCancellation {
        try {
            close()
        } catch (t: Throwable){}
    }
}

private suspend fun AsynchronousSocketChannel.aWrite(buffer: ByteBuffer) = suspendCancellableCoroutine<Int> {
    write(buffer, it, AsyncIOHandler)
}

private suspend fun AsynchronousSocketChannel.aWriteFullBuffer(buffer: ByteBuffer) {
    var bytesWritten = 1
    while(bytesWritten>0 && buffer.hasRemaining()) {
        bytesWritten = aWrite(buffer)
    }
}

private suspend fun AsynchronousSocketChannel.aRead(buffer: ByteBuffer) = suspendCancellableCoroutine<Int> {
    read(buffer, it, AsyncIOHandler)
}

private suspend fun AsynchronousSocketChannel.aReadFullBuffer(buffer: ByteBuffer) {
    var bytesRead = 1
    while (bytesRead>0 && buffer.hasRemaining()) {
        bytesRead = aRead(buffer)
    }
}


class OEFNetworkProxy(
    publicKey: String,
    private val oefAddress: String,
    private val port: Int,
    private var handlerContext: CoroutineContext? = null
) : OEFProxy(publicKey), CoroutineScope {

    companion object {
        val log by logger()
        const val INT_SIZE = 4
    }

    private var socketChannel: AsynchronousSocketChannel = AsynchronousSocketChannel.open()
    private val context: CoroutineContext = SupervisorJob() + Dispatchers.IO
    private var providedContext: Boolean  = true
    private val messageChannel: Channel<Envelope> = Channel(Channel.UNLIMITED)

    init {
        handlerContext = handlerContext ?: let {
            providedContext = false
            SupervisorJob() + Dispatchers.Default
        }
    }

    override val coroutineContext: CoroutineContext
        get() = context


    private suspend fun send(data: AbstractMessage) {
        val sizeBuffer =  ByteBuffer.allocate(INT_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        sizeBuffer.putInt(data.serializedSize)
        sizeBuffer.flip()
        socketChannel.aWriteFullBuffer(sizeBuffer)
        log.info("sending data of size: ${data.serializedSize}; $data")
        val byteBuffer: ByteBuffer = ByteBuffer.wrap(data.toByteArray())
        socketChannel.aWriteFullBuffer(byteBuffer)
    }

    private suspend fun receive(): ByteBuffer {
        val sizeBuffer = ByteBuffer.allocate(INT_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        try {
            socketChannel.aReadFullBuffer(sizeBuffer)
            sizeBuffer.flip()
            val size = sizeBuffer.int
            if (size==0) throw ClosedChannelException()
            log.info("Red size: $size")
            val buffer = ByteBuffer.allocate(size)
            socketChannel.aReadFullBuffer(buffer)
            buffer.flip()
            return buffer
        } catch (e: BufferUnderflowException) {
            throw ClosedChannelException()
        }
    }

    private suspend fun handShake(): Boolean {
        //Step 1: Agent --(ID)--> OEF
        val publicKeyMsg = AgentOuterClass.Agent.Server.ID.newBuilder()
            .setPublicKey(publicKey)
            .build()
        send(publicKeyMsg)
        //Step 2: OEF --(Phrase)--> Agent
        val resp1  = receive()
        val phrase = AgentOuterClass.Server.Phrase.parseFrom(resp1)
        if (!phrase.hasPhrase()) return false
        //Step 3: Agent --(Answer)--> OEF
        val answer = AgentOuterClass.Agent.Server.Answer.newBuilder()
            .setAnswer(phrase.phrase)
            .build()
        send(answer)
        //Step 4: OEF --(Connected)--> Agent
        val resp2 = receive()
        val connected = AgentOuterClass.Server.Connected.parseFrom(resp2)
        return connected.status
    }

    private suspend fun readIOLoop() {
        while (isActive) {
            try {
                val data = receive()
                log.info("Got data: $data")
                launch(handlerContext as CoroutineContext) {
                    processMessage(data)
                }
            }
            catch (e: CancellationException) {}
            catch (e: ClosedChannelException){
                log.warn("Connection with remote OEF-CORE dropped!")
                break
            }
            catch (e: Throwable) {
                log.warn("Exception in socket read loop!", e)
                break
            }
        }
    }

    private suspend fun writeIOLoop() {
        for (msg in messageChannel) {
            try {
                send(msg)
            } catch (e: ClosedChannelException){
                break
            }
        }
    }

    override fun connect(): Boolean = runBlocking {
        try {
            log.info("Connect to OEF @ $oefAddress:$port")
            socketChannel.aConnect(InetSocketAddress(oefAddress, port))
            socketChannel.remoteAddress ?: return@runBlocking false
        } catch (e: ConnectException) {
            log.warn("Failed to establish network connection!")
            stop()
            return@runBlocking false
        } catch (e: Exception) {
            log.warn("Failed to establish network connection!", e)
            return@runBlocking false
        }
        val connectionStatus = try {
            handShake()
        } catch (e :Exception) {
            log.error("Handshake with the server failed!", e)
            false
        }
        if (connectionStatus) {
            launch(context){
                readIOLoop()
                stop()
            }
            launch(context){
                writeIOLoop()
                stop()
            }
        }
        return@runBlocking connectionStatus

    }

    override fun stop() {
        log.info("Stopping...")
        try {
            messageChannel.close()
        } catch (e: Exception){ }
        context.cancelChildren()
        context.cancel()
        if (!providedContext) {
            handlerContext?.cancelChildren()
            handlerContext?.cancel()
        }
        try {
            socketChannel.close()
        } catch (e: Exception) {}
    }

    override suspend fun delayUntilStopped() {
        while (isActive) {
            delay(500L)
        }
    }

    /**
     * Register agent with the OEF.
     */
    override fun registerAgent(messageId: Int, agentDescription: Description)  {
        messageChannel.offer(RegisterDescription(messageId, agentDescription).toEnvelope())
    }

    /**
     * Unregister agent with the OEF.
     */
    override fun unregisterAgent(messageId: Int) {
        messageChannel.offer(UnregisterDescription(messageId).toEnvelope())
    }

    /**
     * Register service with the OEF.
     */
    override fun registerService(messageId: Int, serviceDescription: Description) {
        messageChannel.offer(RegisterService(messageId, serviceDescription).toEnvelope())
    }

    /**
     * Unregister service with the OEF.
     */
    override fun unregisterService(messageId: Int, serviceDescription: Description) {
        messageChannel.offer(RegisterService(messageId, serviceDescription).toEnvelope())
    }

    /**
     * Search for agents
     */
    override fun searchAgents(searchId: Int, query: Query) {
        messageChannel.offer(SearchAgents(searchId, query).toEnvelope())
    }

    /**
     * Search for services
     */
    override fun searchServices(searchId: Int, query: Query) {
        messageChannel.offer(SearchServices(searchId, query).toEnvelope())
    }

    /**
     * Wide search on the OEF Network
     */
    override fun searchSericesWide(searchId: Int, query: Query) {
        messageChannel.offer(SearchServicesWide(searchId, query).toEnvelope())
    }

    /**
     * Send byte message to the other agent
     */
    override fun sendMessage(messageId: Int, dialogueId: Int, destination: String, message: ByteBuffer) {
        messageChannel.offer(Message(messageId, dialogueId, destination, message).toEnvelope())
    }

    /**
     * Send CFP to the other agent
     */
    override fun sendCFP(messageId: Int, dialogueId: Int, destination: String,  target: Int, query: CFPQuery) {
        messageChannel.offer(CFP(messageId, dialogueId, destination, target, query).toEnvelope())
    }

    /**
     * Send proposal to the other agent
     */
    override fun sendPropose(messageId: Int, dialogueId: Int, destination: String,  target: Int, proposals: Proposals) {
        messageChannel.offer(Propose(messageId, dialogueId, destination, target, proposals).toEnvelope())
    }

    /**
     * Send accept message to the other agent
     */
    override fun sendAccept(messageId: Int, dialogueId: Int, destination: String, target: Int) {
        messageChannel.offer(Accept(messageId, dialogueId, destination, target).toEnvelope())
    }

    /**
     * Send decline message to the other agent
     */
    override fun sendDecline( messageId: Int, dialogueId: Int, destination: String, target: Int) {
        messageChannel.offer(Decline(messageId, dialogueId, destination, target).toEnvelope())
    }
}

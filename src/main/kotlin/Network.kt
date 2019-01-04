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
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.AsynchronousCloseException
import java.nio.channels.AsynchronousSocketChannel
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
    private val port: Int = DEFAULT_OEF_PORT,
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
        log.info("sending data of size: ${data.serializedSize}")
        val byteBuffer: ByteBuffer = ByteBuffer.wrap(data.toByteArray())
        socketChannel.aWriteFullBuffer(byteBuffer)
    }

    private suspend fun receive(): ByteBuffer {
        val sizeBuffer = ByteBuffer.allocate(OEFNetworkProxy.INT_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        socketChannel.aReadFullBuffer(sizeBuffer)
        sizeBuffer.flip()
        val size = sizeBuffer.int
        val buffer = ByteBuffer.allocate(size)
        socketChannel.aReadFullBuffer(buffer)
        buffer.flip()
        return buffer
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
                launch(handlerContext as CoroutineContext) {
                    processMessage(data)
                }
            }
            catch (e: CancellationException){}
            catch (e: Throwable) {
                log.warn("Exception in socket read loop!", e)
            }
        }
    }

    private suspend fun writeIOLoop() {
        for (msg in messageChannel) {
            send(msg)
        }
    }

    override fun connect(): Boolean = runBlocking {
        try {
            socketChannel.aConnect(InetSocketAddress(oefAddress, port))
            socketChannel.remoteAddress ?: return@runBlocking false
        } catch (e: Exception) {
            log.warn("Failed to establish network connection! ", e)
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
            }
            launch(context){
                writeIOLoop()
            }
        }
        return@runBlocking connectionStatus

    }

    override fun stop() {
        messageChannel.close()
        context.cancelChildren()
        context.cancel()
        if (!providedContext) {
            handlerContext?.cancelChildren()
            handlerContext?.cancel()
        }
        socketChannel.close()
    }

    override suspend fun delayUntilStopped() {
        while (isActive) {
            delay(500L)
        }
    }

    /**
     * Register agent with the OEF.
     */
    override fun registerAgent(agentDescription: Description)  {
        messageChannel.offer(RegisterDescription(agentDescription).toEnvelope())
    }

    /**
     * Unregister agent with the OEF.
     */
    override fun unregisterAgent() {
        messageChannel.offer(UnregisterDescription().toEnvelope())
    }

    /**
     * Register service with the OEF.
     */
    override fun registerService(serviceDescription: Description) {
        messageChannel.offer(RegisterService(serviceDescription).toEnvelope())
    }

    /**
     * Unregister service with the OEF.
     */
    override fun unregisterService(serviceDescription: Description) {
        messageChannel.offer(RegisterService(serviceDescription).toEnvelope())
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
     * Send byte message to the other agent
     */
    override fun sendMessage(dialogueId: Int, destination: String, message: ByteBuffer) {
        messageChannel.offer(Message(dialogueId, destination, message).toEnvelope())
    }

    /**
     * Send CFP to the other agent
     */
    override fun sendCFP(dialogueId: Int, destination: String, query: CFPQuery, messageId: Int, target: Int) {
        messageChannel.offer(CFP(dialogueId, destination, query, messageId, target).toEnvelope())
    }

    /**
     * Send proposal to the other agent
     */
    override fun sendPropose(dialogueId: Int, destination: String, proposals: Proposals, messageId: Int, target: Int?) {
        messageChannel.offer(Propose(dialogueId, destination, proposals, messageId, target).toEnvelope())
    }

    /**
     * Send accept message to the other agent
     */
    override fun sendAccept(dialogueId: Int, destination: String, messageId: Int, target: Int?) {
        messageChannel.offer(Accept(dialogueId, destination, messageId, target).toEnvelope())
    }

    /**
     * Send decline message to the other agent
     */
    override fun sendDecline(dialogueId: Int, destination: String, messageId: Int, target: Int?) {
        messageChannel.offer(Decline(dialogueId, destination, messageId, target).toEnvelope())
    }
}

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
import java.io.IOException
import java.lang.Exception
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.util.concurrent.LinkedBlockingQueue
import kotlin.coroutines.CoroutineContext


const val DEFAULT_OEF_PORT = 3333

@Deprecated("Use OEFNetworkProxyAsync instead")
class OEFNetworkProxy(
    publicKey: String,
    private val oefAddress: String,
    private val port: Int = DEFAULT_OEF_PORT
) : OEFProxy(publicKey), CoroutineScope {

    companion object {
        val log by logger()
        const val INT_SIZE = 4
    }

    private var socketChannel: SocketChannel = SocketChannel.open()
    private var selector: Selector           = Selector.open()
    private val pendingOutboundMessages      = LinkedBlockingQueue<Envelope>()
    private var activeIOLoop: Boolean        = true
    private var connectionLoop: Boolean      = true
    private lateinit var context: CoroutineContext

    init {
        socketChannel.configureBlocking(false)
        socketChannel.connect(InetSocketAddress(oefAddress, port))
        socketChannel.register(selector, SelectionKey.OP_CONNECT or SelectionKey.OP_WRITE or SelectionKey.OP_READ)
    }

    private fun initContext() {
        context = SupervisorJob() + Dispatchers.IO
        launch {
            ioLoop()
        }
    }

    private fun destroyContext() {
        context.cancelChildren()
        context.cancel()
    }

    override val coroutineContext: CoroutineContext
        get() = context

    private fun ioLoop() {
        launch {
            while (activeIOLoop){
                pendingOutboundMessages.take()?.let{
                    launch {
                        send(socketChannel, it)
                    }
                }
            }
        }
        while(activeIOLoop) {
            val readyChannels = selector.select()
            if (readyChannels == 0) continue
            for (key in selector.selectedKeys()) {
                if (key.isReadable) {
                    launch {
                        try {
                            val data = receive(key.channel() as SocketChannel)
                            processMessage(data)
                        } catch (e: Exception) {
                            log.warn("Exception in socket read loop!", e)
                        }
                    }
                }
                if (key.isWritable) {
                    log.warn("write loop")
                }
            }
        }
    }

    private fun send(channel: SocketChannel, data: AbstractMessage) {
        val sizeBuffer =  ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        sizeBuffer.putInt(data.serializedSize)
        sizeBuffer.flip()
        log.info("sending data of size: ${data.serializedSize}")
        var bytesWritten = 1
        while(bytesWritten>0 && sizeBuffer.hasRemaining()) {
            channel.write(sizeBuffer)
        }
        val byteBuffer: ByteBuffer = ByteBuffer.wrap(data.toByteArray())
        bytesWritten = 1
        while(bytesWritten>0 && byteBuffer.hasRemaining()) {
            channel.write(byteBuffer)
        }
    }

    private fun receive(channel: SocketChannel): ByteBuffer {
        val sizeBuffer = ByteBuffer.allocate(INT_SIZE).order(ByteOrder.LITTLE_ENDIAN)

        var bytesRead = 1
        while (bytesRead>0 && sizeBuffer.hasRemaining()) {
            bytesRead = channel.read(sizeBuffer)
        }
        sizeBuffer.flip()
        val size = sizeBuffer.int
        val buffer = ByteBuffer.allocate(size)
        bytesRead = 1
        while(bytesRead>0 && buffer.hasRemaining()){
            bytesRead = channel.read(buffer)
        }
        buffer.flip()
        return buffer
    }

    private fun handShake(channel: SocketChannel): Boolean {
        //Step 1: Agent --(ID)--> OEF
        val publicKeyMsg = AgentOuterClass.Agent.Server.ID.newBuilder()
            .setPublicKey(publicKey)
            .build()
        send(channel, publicKeyMsg)
        //Step 2: OEF --(Phrase)--> Agent
        val resp1  = receive(channel)
        val phrase = AgentOuterClass.Server.Phrase.parseFrom(resp1)
        if (!phrase.hasPhrase()) return false
        //Step 3: Agent --(Answer)--> OEF
        val answer = AgentOuterClass.Agent.Server.Answer.newBuilder()
            .setAnswer(phrase.phrase)
            .build()
        send(channel, answer)
        //Step 4: OEF --(Connected)--> Agent
        val resp2 = receive(channel)
        val connected = AgentOuterClass.Server.Connected.parseFrom(resp2)
        return connected.status
    }

    override fun connect(): Boolean {
        while(connectionLoop){
            val readyChannels = selector.select()
            if (readyChannels==0) continue
            for(key in selector.selectedKeys()){
                if (key.isConnectable){
                    val channel = key.channel() as SocketChannel
                    try {
                        while (channel.isConnectionPending) {
                            channel.finishConnect()
                        }
                    } catch (e: IOException){
                        log.error("Failed to establish network connection!", e)
                        return false
                    }
                }
                if (key.isWritable){
                    val channel = key.channel() as SocketChannel
                    key.cancel()
                    selector.selectNow()
                    return try {
                        channel.configureBlocking(true)
                        handShake(channel)
                    } catch (e: Exception) {
                        log.error("Handshake with the server failed!", e)
                        false
                    } finally {
                        try {
                            channel.configureBlocking(false)
                            channel.register(selector, SelectionKey.OP_READ or SelectionKey.OP_WRITE)
                            log.info("Connection established with OEF node $oefAddress:$port")
                            activeIOLoop   = true
                            connectionLoop = false
                            initContext()
                        } catch (e: Exception) {
                            log.warn("Failed to switch back to non-blocking mode!", e)
                        }
                    }
                } else {
                    continue
                }
            }
        }
        return true
    }

    override fun stop() {
        activeIOLoop   = false
        connectionLoop = true
        socketChannel.close()
        selector.close()
        destroyContext()
    }

    override fun registerAgent(agentDescription: Description) {
        log.info("Register agent. Current list size: ${pendingOutboundMessages.size}")
        pendingOutboundMessages.add(RegisterDescription(agentDescription).toEnvelope())
    }

    override fun unregisterAgent() {
        pendingOutboundMessages.add(UnregisterDescription().toEnvelope())
    }

    override fun registerService(serviceDescription: Description) {
        pendingOutboundMessages.add(RegisterService(serviceDescription).toEnvelope())
    }

    override fun unregisterService(serviceDescription: Description) {
        pendingOutboundMessages.add(RegisterService(serviceDescription).toEnvelope())
    }

    override fun searchAgents(searchId: Int, query: Query) {
        pendingOutboundMessages.add(SearchAgents(searchId, query).toEnvelope())
    }

    override fun searchServices(searchId: Int, query: Query) {
        pendingOutboundMessages.add(SearchServices(searchId, query).toEnvelope())
    }

    override fun sendMessage(dialogueId: Int, destination: String, message: ByteBuffer) {
        pendingOutboundMessages.add(Message(dialogueId, destination, message).toEnvelope())
    }

    override fun sendCFP(dialogueId: Int, destination: String, query: CFPQuery, messageId: Int, target: Int) {
        pendingOutboundMessages.add(CFP(dialogueId, destination, query, messageId, target).toEnvelope())
    }

    override fun sendPropose(dialogueId: Int, destination: String, proposals: Proposals, messageId: Int, target: Int?) {
        pendingOutboundMessages.add(Propose(dialogueId, destination, proposals, messageId, target).toEnvelope())
    }

    override fun sendAccept(dialogueId: Int, destination: String, messageId: Int, target: Int?) {
        pendingOutboundMessages.add(Accept(dialogueId, destination, messageId, target).toEnvelope())
    }

    override fun sendDecline(dialogueId: Int, destination: String, messageId: Int, target: Int?) {
        pendingOutboundMessages.add(Decline(dialogueId, destination, messageId, target).toEnvelope())
    }
}
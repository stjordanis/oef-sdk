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

import fetch.oef.pb.AgentOuterClass
import fetch.oef.pb.FipaOuterClass
import java.nio.ByteBuffer
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private typealias PayloadCase        = AgentOuterClass.Server.AgentMessage.PayloadCase
private typealias ContentPayloadCase = AgentOuterClass.Server.AgentMessage.Content.PayloadCase
private typealias MsgCase            = FipaOuterClass.Fipa.Message.MsgCase

abstract class OEFProxy (
    protected val publicKey: String
) : OEFProxyInterface, OEFDelayInterface {


    companion object {
        val log by logger()
    }

    private val lock = ReentrantLock()
    private val agentSetCondition = lock.newCondition()
    private lateinit var agent: AgentCommunicationHandlerInterface

    internal abstract fun connect(): Boolean
    internal abstract fun stop()

    internal fun setAgent(agent: AgentCommunicationHandlerInterface) {
        if (this::agent.isInitialized){
            log.warn("Agent already set!")
            return
        }
        this.agent = agent
        lock.withLock {
            agentSetCondition.signalAll()
        }
    }

    protected fun processMessage(data: ByteBuffer) = try {
        if (!::agent.isInitialized) {
            lock.withLock {
                while (!::agent.isInitialized) {
                    agentSetCondition.await()
                }
            }
        }
        val msg = AgentOuterClass.Server.AgentMessage.parseFrom(data)
        when(msg.payloadCase) {
            PayloadCase.AGENTS -> msg.agents?.run {
                agent.onSearchResult(searchId, agentsList)
            }
            PayloadCase.ERROR -> msg.error?.run {
                agent.onError(operation, dialogueId, msgId)
            }
            PayloadCase.CONTENT -> msg.content?.run {
                when (payloadCase) {
                    ContentPayloadCase.CONTENT -> agent.onMessage(dialogueId, origin, content.asReadOnlyByteBuffer())
                    ContentPayloadCase.FIPA -> fipa?.let { fipa ->
                        when (fipa.msgCase) {
                            MsgCase.CFP -> fipa.cfp?.let { cfp ->
                                agent.onCFP(dialogueId, origin, fipa.msgId, fipa.target, CFPQuery.fromProto(cfp))
                            }
                            MsgCase.PROPOSE -> fipa.propose?.let { propose ->
                                agent.onPropose(
                                    dialogueId,
                                    origin,
                                    fipa.msgId,
                                    fipa.target,
                                    Proposals.fromProto(propose)
                                )
                            }
                            MsgCase.ACCEPT -> agent.onAccept(dialogueId, origin, fipa.msgId, fipa.target)
                            MsgCase.DECLINE -> agent.onDecline(dialogueId, origin, fipa.msgId, fipa.target)
                            else -> {
                                log.warn("Not implemented yet: fipa ${fipa.msgCase.name}")
                            }
                        }
                    }
                    else -> {
                        log.warn("Not supported content type: ${payloadCase.name}")
                    }
                }
            }
            else -> {
                log.warn("Not supported payload case in AgentMessage (${msg.payloadCase.name})! ")
            }
        }
    } catch (e: Throwable) {
        log.error("Failed to process server message!",  e)
    }
}
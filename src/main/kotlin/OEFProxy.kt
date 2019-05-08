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
package ai.fetch.oef

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
            PayloadCase.OEF_ERROR -> msg.oefError?.run {
                agent.onOEFError(msg.answerId, OEFError.fromProto(this))
            }
            PayloadCase.DIALOGUE_ERROR -> msg.dialogueError?.run {
                agent.onDialogueError(msg.answerId, dialogueId)
            }
            PayloadCase.AGENTS -> msg.agents?.run {
                agent.onSearchResult(msg.answerId, agentsList)
            }
            PayloadCase.AGENTS_WIDE -> msg.agentsWide?.run {
                agent.onSearchResultWide(msg.answerId, resultList.map {
                    SearchResultItem(
                        it.key.toStringUtf8(),
                        it.ip+":"+it.port.toString(),
                        it.distance,
                        it.info,
                        it.agentsList.map {a->
                            SearchResultItem.AgentInfo(a.key.toStringUtf8(), a.score)
                        }
                    )
                })
            }
            PayloadCase.CONTENT -> msg.content?.run {
                when (payloadCase) {
                    ContentPayloadCase.CONTENT -> agent.onMessage(msg.answerId, dialogueId, origin, content.asReadOnlyByteBuffer())
                    ContentPayloadCase.FIPA -> fipa?.let { fipa ->
                        when (fipa.msgCase) {
                            MsgCase.CFP -> fipa.cfp?.let { cfp ->
                                agent.onCFP(msg.answerId, dialogueId, origin, fipa.target, CFPQuery.fromProto(cfp))
                            }
                            MsgCase.PROPOSE -> fipa.propose?.let { propose ->
                                agent.onPropose(msg.answerId, dialogueId, origin, fipa.target, Proposals.fromProto(propose))
                            }
                            MsgCase.ACCEPT -> agent.onAccept(msg.answerId, dialogueId, origin, fipa.target)
                            MsgCase.DECLINE -> agent.onDecline(msg.answerId, dialogueId, origin, fipa.target)
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
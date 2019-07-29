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

import com.google.protobuf.ByteString
import fetch.oef.pb.FipaOuterClass
import java.io.Closeable
import java.nio.ByteBuffer
import kotlin.Exception

/**
 * ProtobufSerializable interface
 */
internal interface ProtobufSerializable <T> {
    fun toProto() : T
    fun fromProto(obj: T)
}

/**
 * OEF Core interface
 */

interface OEFCommunicationErrorHandlerInterface {
    fun onOEFError(messageId: Int, error: OEFError)
    fun onDialogueError(messageId: Int, dialogueId: Int)
}

interface OEFDelayInterface {
    suspend fun delayUntilStopped()
}

interface OEFConnectionInterface : Closeable {
    fun connect(): Boolean
    fun stop()

    override fun close() {
        stop()
    }
}

interface OEFAgentRegisterInterface {
    fun registerAgent(messageId: Int, agentDescription: Description)
    fun unregisterAgent(messageId: Int)
}

interface OEFServiceRegisterInterface {
    fun registerService(messageId: Int, serviceDescription: Description, serviceId: String = "")
    fun unregisterService(messageId: Int, serviceDescription: Description, serviceId: String = "")
}

interface OEFSearchInterface {
    fun searchAgents(searchId: Int, query: Query)
    fun searchServices(searchId: Int, query: Query)
    fun searchSericesWide(searchId: Int, query: Query)
}

interface OEFSearchHandlerInterface {
    fun onSearchResult(searchId: Int, agents: List<String>)
    fun onSearchResultWide(searchId: Int, result: List<SearchResultItem>)
}


data class SearchResultItem(
    val key: String,
    val uri: String,
    val distance: Double,
    val info: String,
    val agents: List<AgentInfo>

) {
    data class AgentInfo(
        val key: String,
        val score: Double
    )
}


class TypeError(msg: String) : Exception(msg)

private typealias FipaCfp            = FipaOuterClass.Fipa.Cfp
private typealias FipaPropose        = FipaOuterClass.Fipa.Propose
private typealias CFPPayloadCase     = FipaOuterClass.Fipa.Cfp.PayloadCase
private typealias ProposePayloadCase = FipaOuterClass.Fipa.Propose.PayloadCase

sealed class CFPQuery {
    data class TQuery(val value: Query)      : CFPQuery()
    data class TBytes(val value: ByteBuffer) : CFPQuery()
    object     TNone                         : CFPQuery()

    companion object {
        fun fromProto(cfp: FipaCfp): CFPQuery = when(cfp.payloadCase) {
            CFPPayloadCase.NOTHING -> TNone
            CFPPayloadCase.CONTENT -> TBytes(cfp.content.asReadOnlyByteBuffer())
            CFPPayloadCase.QUERY   -> TQuery(Query.fromProto(cfp.query))
            else -> {
                throw TypeError("Query type not valid!")
            }
        }

        fun toProto(query: CFPQuery): FipaCfp = FipaCfp.newBuilder().apply {
            when (query) {
                is TNone  -> setNothing(FipaOuterClass.Fipa.Cfp.Nothing.newBuilder())
                is TQuery -> setQuery(query.value.toProto())
                is TBytes -> content = ByteString.copyFrom(query.value)
            }
        }.build()
    }
}

fun cfpQueryFrom()                  = CFPQuery.TNone
fun cfpQueryFrom(query: Query)      = CFPQuery.TQuery(query)
fun cfpQueryFrom(bytes: ByteBuffer) = CFPQuery.TBytes(bytes)

sealed class Proposals {
    data class TBytes(val value: ByteBuffer)               : Proposals()
    data class TDescriptions(val value: List<Description>) : Proposals()

    companion object {
        fun fromProto(propose: FipaPropose): Proposals = when(propose.payloadCase){
            ProposePayloadCase.CONTENT -> TBytes(propose.content.asReadOnlyByteBuffer())
            else -> TDescriptions(propose.proposals?.objectsList?.map{proposal->
                Description().apply {
                    fromProto(proposal)
                }
            } ?: listOf())
        }

        fun toProto(proposal: Proposals): FipaPropose = FipaPropose.newBuilder().apply {
            when(proposal){
                is TBytes        -> content   = ByteString.copyFrom(proposal.value)
                is TDescriptions -> proposals = FipaOuterClass.Fipa.Propose.Proposals.newBuilder()
                    .apply {
                        proposal.value.forEach {
                            addObjects(it.toProto())
                        }
                    }
                    .build()
            }
        }.build()
    }
}

fun proposalsFrom(vararg proposals: Description) = Proposals.TDescriptions(proposals.asList())
fun proposalsFrom(bytes: ByteBuffer)             = Proposals.TBytes(bytes)

class Context {
    var targetURI: OEFURI = OEFURI()
    var sourceURI: OEFURI = OEFURI()
    var serviceId: String = ""
    var agentAlias: String = ""

    fun update(target: String, source: String) {
        targetURI.parse(target)
        sourceURI.parse(source)
        serviceId = targetURI.agentAlias
        agentAlias = serviceId
    }

    fun swap() {
        val tmp = targetURI
        targetURI = sourceURI
        sourceURI = tmp
        serviceId = targetURI.agentAlias
        agentAlias = targetURI.agentAlias
    }

    fun forAgent(target: String, source: String, same_alias: Boolean = false) {
        targetURI.parseAgent(target)
        sourceURI.parseAgent(source)
        if (same_alias) {
            sourceURI.agentAlias = targetURI.agentAlias
        }
    }
}

interface AgentMessageEmmiterInterface {
    fun sendMessage(messageId: Int, dialogueId: Int, destination: String, message: ByteBuffer, context: Context = Context())
    fun sendCFP    (messageId: Int, dialogueId: Int, destination: String, target: Int, query: CFPQuery, context: Context = Context())
    fun sendPropose(messageId: Int, dialogueId: Int, destination: String, target: Int, proposals: Proposals, context: Context = Context())
    fun sendAccept (messageId: Int, dialogueId: Int, destination: String, target: Int, context: Context = Context())
    fun sendDecline(messageId: Int, dialogueId: Int, destination: String, target: Int, context: Context = Context())
}

interface AgentMessageHandlerInterface {
    fun onMessage(answerId: Int, dialogueId: Int, origin: String, content: ByteBuffer)
    fun onCFP    (answerId: Int, dialogueId: Int, origin: String, target: Int, query: CFPQuery)
    fun onPropose(answerId: Int, dialogueId: Int, origin: String, target: Int, proposals: Proposals)
    fun onAccept (answerId: Int, dialogueId: Int, origin: String, target: Int)
    fun onDecline(answerId: Int, dialogueId: Int, origin: String, target: Int)
}

interface OEFProxyInterface :
        OEFAgentRegisterInterface,
        OEFServiceRegisterInterface,
        OEFSearchInterface,
        AgentMessageEmmiterInterface {

    fun getContext(messageId: Int, dialogueId: Int, origin: String): Context
}

interface AgentCommunicationHandlerInterface :
        OEFCommunicationErrorHandlerInterface,
        OEFSearchHandlerInterface,
        AgentMessageHandlerInterface

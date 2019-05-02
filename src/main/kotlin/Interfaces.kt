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

import com.google.protobuf.ByteString
import fetch.oef.pb.AgentOuterClass
import fetch.oef.pb.FipaOuterClass
import kotlinx.coroutines.Job
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
    fun registerService(messageId: Int, serviceDescription: Description)
    fun unregisterService(messageId: Int, serviceDescription: Description)
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
            CFPPayloadCase.NOTHING -> CFPQuery.TNone
            CFPPayloadCase.CONTENT -> CFPQuery.TBytes(cfp.content.asReadOnlyByteBuffer())
            CFPPayloadCase.QUERY   -> CFPQuery.TQuery(Query.fromProto(cfp.query))
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
            ProposePayloadCase.CONTENT -> Proposals.TBytes(propose.content.asReadOnlyByteBuffer())
            else -> Proposals.TDescriptions(propose.proposals?.objectsList?.map{proposal->
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

interface AgentMessageEmmiterInterface {
    fun sendMessage(messageId: Int, dialogueId: Int, destination: String, message: ByteBuffer)
    fun sendCFP    (messageId: Int, dialogueId: Int, destination: String, target: Int, query: CFPQuery)
    fun sendPropose(messageId: Int, dialogueId: Int, destination: String, target: Int, proposals: Proposals)
    fun sendAccept (messageId: Int, dialogueId: Int, destination: String, target: Int)
    fun sendDecline(messageId: Int, dialogueId: Int, destination: String, target: Int)
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
        AgentMessageEmmiterInterface

interface AgentCommunicationHandlerInterface :
        OEFCommunicationErrorHandlerInterface,
        OEFSearchHandlerInterface,
        AgentMessageHandlerInterface

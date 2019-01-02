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
    fun onError(operation: AgentOuterClass.Server.AgentMessage.Error.Operation, dialogueId: Int, messageId: Int)
}

interface OEFDelayInterface {
    fun delay(untilAgentStopped: Boolean, time: Long = 1000L): Job
}

interface OEFConnectionInterface : Closeable {
    fun connect(): Boolean
    fun stop()

    override fun close() {
        stop()
    }
}

interface OEFAgentRegisterInterface {
    fun registerAgent(agentDescription: Description): Job
    fun unregisterAgent(): Job
}

interface OEFServiceRegisterInterface {
    fun registerService(serviceDescription: Description): Job
    fun unregisterService(serviceDescription: Description): Job
}

interface OEFSearchInterface {
    fun searchAgents(searchId: Int, query: Query): Job
    fun searchServices(searchId: Int, query: Query): Job
}

interface OEFSearchHandlerInterface {
    fun onSearchResult(searchId: Int, agents: List<String>)
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
    fun sendMessage(dialogueId: Int, destination: String, message: ByteBuffer): Job
    fun sendCFP    (dialogueId: Int, destination: String, query: CFPQuery, messageId: Int = 1, target: Int = 0): Job
    fun sendPropose(dialogueId: Int, destination: String, proposals: Proposals, messageId: Int, target: Int? = null): Job
    fun sendAccept (dialogueId: Int, destination: String, messageId: Int, target: Int? = null): Job
    fun sendDecline(dialogueId: Int, destination: String, messageId: Int, target: Int? = null): Job
}

interface AgentMessageHandlerInterface {
    fun onMessage(dialogueId: Int, origin: String, content: ByteBuffer)
    fun onCFP    (dialogueId: Int, origin: String, messageId: Int, target: Int, query: CFPQuery)
    fun onPropose(dialogueId: Int, origin: String, messageId: Int, target: Int, proposals: Proposals)
    fun onAccept (dialogueId: Int, origin: String, messageId: Int, target: Int)
    fun onDecline(dialogueId: Int, origin: String, messageId: Int, target: Int)
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

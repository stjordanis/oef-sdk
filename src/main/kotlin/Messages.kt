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
import java.nio.ByteBuffer

internal typealias Envelope           = AgentOuterClass.Envelope
private typealias Nothing             = AgentOuterClass.Envelope.Nothing
private typealias AgentSearch         = AgentOuterClass.AgentSearch
private typealias AgentMessagePb      = AgentOuterClass.Agent.Message
private typealias FipaMessagePb       = FipaOuterClass.Fipa.Message
private typealias AgentMessageBuilder = AgentOuterClass.Agent.Message.Builder
private typealias FipaMessageBuilder  = FipaOuterClass.Fipa.Message.Builder
private typealias FipaAccept          = FipaOuterClass.Fipa.Accept
private typealias FipaDecline         = FipaOuterClass.Fipa.Decline
private typealias OEFErrorOperation   = AgentOuterClass.Server.AgentMessage.OEFError.Operation

enum class OEFError {
    REGISTER_SERVICE,
    UNREGISTER_SERVICE,
    REGISTER_DESCRIPTION,
    UNREGISTER_DESCRIPTION,
    UNKNOWN;

    companion object {
        fun fromProto(pb: AgentOuterClass.Server.AgentMessage.OEFError): OEFError = when(pb.operation){
            OEFErrorOperation.REGISTER_SERVICE -> REGISTER_SERVICE
            OEFErrorOperation.UNREGISTER_SERVICE -> UNREGISTER_SERVICE
            OEFErrorOperation.REGISTER_DESCRIPTION -> REGISTER_DESCRIPTION
            OEFErrorOperation.UNREGISTER_DESCRIPTION -> UNREGISTER_DESCRIPTION
            else -> UNKNOWN
        }
    }
}


interface BaseMessage {
    fun toEnvelope(): Envelope
}

private fun newEnvelopeBuilder(messageId: Int) = Envelope.newBuilder()
    .setMsgId(messageId)

/**
 *  This message is used for registering a new agent  in the Agent Directory of an OEF Node.
 *  The agent is described by a :class:`~oef.schema.Description` object.
 */
class RegisterDescription (
    private val msgId: Int,
    private val agentDescription: Description
) : BaseMessage {
    override fun toEnvelope(): Envelope = newEnvelopeBuilder(msgId)
        .setRegisterDescription(agentDescription.toAgentDescription())
        .build()
}

/**
 * This message is used for registering a new agent in the Service Directory of an OEF Node.
 * The service agent is described by a :class:`~oef.schema.Description` object.
 */
class RegisterService (
    private val msgId: Int,
    private val serviceDescription: Description
) : BaseMessage {
    override fun toEnvelope(): Envelope = newEnvelopeBuilder(msgId)
        .setRegisterService(serviceDescription.toAgentDescription())
        .build()
}

/**
 * This message is used for unregistering an agent in the Agent Directory of an OEF Node.
 */
class UnregisterDescription (
    private val msgId: Int
) : BaseMessage {
    override fun toEnvelope(): Envelope = newEnvelopeBuilder(msgId)
        .setUnregisterDescription(Nothing.newBuilder().build())
        .build()
}

/**
 * This message is used for unregistering a `(service agent, description)` in the Service Directory of an OEF Node.
 */
class UnregisterService (
    private val msgId: Int,
    private val serviceDescription: Description
): BaseMessage {
    override fun toEnvelope(): Envelope = newEnvelopeBuilder(msgId)
        .setUnregisterService(serviceDescription.toAgentDescription())
        .build()
}

/**
 * This message is used for searching agents in the Agent Directory of an OEF Node.
 * It contains:
 *  - a search id, that identifies the search query. This id will be used by the sender
 *    in order to distinguish different incoming search results.
 *  - a query, i.e. a list of constraints defined over a data model.
 */
class SearchAgents(
    private val searchId: Int,
    private val query: Query
): BaseMessage {
    override fun toEnvelope(): Envelope = newEnvelopeBuilder(searchId)
        .setSearchAgents(
            AgentSearch.newBuilder()
                .setQuery(query.toProto())
        )
        .build()
}

/**
 * This message is used for searching services in the Service Directory of an OEF Node.
 * It contains:
 *      - a search id, that identifies the search query. This id will be used
 *         by the sender in order to distinguish different incoming search results.
 *      - a query, i.e. a list of constraints defined over a data model.
 */
class SearchServices (
    private val searchId: Int,
    private val query: Query
) : BaseMessage {
    override fun toEnvelope(): Envelope = newEnvelopeBuilder(searchId)
        .setSearchServices(
            AgentSearch.newBuilder()
                .setQuery(query.toProto())
        )
        .build()
}

/**
 * This message is used for searching agents in OEF Network.
 * It contains:
 *  - a search id, that identifies the search query. This id will be used by the sender
 *    in order to distinguish different incoming search results.
 *  - a query, i.e. a list of constraints defined over a data model.
 */
class SearchServicesWide(
    private val searchId: Int,
    private val query: Query
): BaseMessage {
    override fun toEnvelope(): Envelope = newEnvelopeBuilder(searchId)
        .setSearchServicesWide(
            AgentSearch.newBuilder()
                .setQuery(query.toProto())
        )
        .build()
}


/**
 *  This type of message is used for interacting with other agents, via an OEF Node.
 */
interface AgentMessage : BaseMessage


private fun createEnvelopeWithAgentMessageBuilder(
    messageId: Int,
    dialogueId: Int,
    destination: String,
    block: AgentMessageBuilder.()->Unit
) =  newEnvelopeBuilder(messageId)
    .setSendMessage(
        AgentMessagePb.newBuilder()
            .setDialogueId(dialogueId)
            .setDestination(destination)
            .apply(block)
    )

private fun createFipaMessage(
    targetId: Int,
    block: FipaMessageBuilder.()->Unit) = FipaMessagePb.newBuilder()
    .setTarget(targetId)
    .apply(block)
    .build()


/**
 * This message is used to send a generic message to other agents.
 * It contains:
 *      - a dialogue id, that identifies the dialogue in which the message is sent.
 *      - a destination, that is the public key of the recipient of the message.
 *      -  a sequence of bytes, that is the content of the message.
 */
class Message(
    private val messageId: Int,
    private val dialogueId: Int,
    private val destination: String,
    private val message: ByteBuffer
) : AgentMessage {
    override fun toEnvelope(): Envelope = createEnvelopeWithAgentMessageBuilder(messageId, dialogueId, destination) {
        content = ByteString.copyFrom(message)
    }.build()
}

/**
 * This message is used to send a `Call For Proposals`.
 * It contains:
 *      - a dialogue id, that identifies the dialogue in which the message is sent.
 *      - a destination, that is the public key of the recipient of the message.
 *      - a query, that describes the resources the sender is interested in.
 *      - a message id, that is an unique identifier for a message, given dialogue.
 *      - a target id, that is, the identifier of the message to whom this message is targeting, in a given dialogue.
 */
class CFP (
    private val messageId: Int,
    private val dialogueId: Int,
    private val destination: String,
    private val targetId: Int,
    private val query: CFPQuery
) : AgentMessage {
    override fun toEnvelope(): Envelope = createEnvelopeWithAgentMessageBuilder(messageId, dialogueId, destination) {
        fipa = createFipaMessage(targetId) {
            cfp = CFPQuery.toProto(query)
        }
    }.build()
}

/**
 * This message is used to send a `Propose`.
 * It contains:
 *      - a dialogue id, that identifies the dialogue in which the message is sent.
 *      - a destination, that is the public key of the recipient of the message.
 *      - a list of proposals describing the resources that the seller proposes.
 *      - the message id, that is an unique identifier for a message, given dialogue.
 *      - target, that is, the identifier of the message to whom this message is targeting.
 */
class Propose (
    private val messageId: Int,
    private val dialogueId: Int,
    private val destination: String,
    private val targetId: Int,
    private val proposals: Proposals
) : AgentMessage {
    override fun toEnvelope(): Envelope = createEnvelopeWithAgentMessageBuilder(messageId, dialogueId, destination) {
        fipa = createFipaMessage(targetId) {
            propose = Proposals.toProto(proposals)
        }
    }.build()
}

/**
 *  This message is used to send an `Accept`.
 *  It contains:
 *      - a dialogue id, that identifies the dialogue in which the message is sent.
 *      - a destination, that is the public key of the recipient of the message.
 *      - the message id, that is an unique identifier for a message, given dialogue.
 *      - target, that is, the identifier of the message to whom this message is targeting.
 */
class Accept (
    private val messageId: Int,
    private val dialogueId: Int,
    private val destination: String,
    private val targetId: Int
) : AgentMessage {
    override fun toEnvelope(): Envelope = createEnvelopeWithAgentMessageBuilder(messageId, dialogueId, destination) {
        fipa = createFipaMessage(targetId) {
            accept = FipaAccept.newBuilder().build()
        }
    }.build()
}

/**
 * This message is used to send an `Decline`.
 * It contains:
 *      - a dialogue id, that identifies the dialogue in which the message is sent.
 *      - a destination, that is the public key of the recipient of the message.
 *      - the message id, that is an unique identifier for a message, given dialogue.
 *      - target, that is, the identifier of the message to whom this message is targeting.
 */
class Decline (
    private val messageId: Int,
    private val dialogueId: Int,
    private val destination: String,
    private val targetId: Int
) : AgentMessage {
    override fun toEnvelope(): Envelope = createEnvelopeWithAgentMessageBuilder(messageId, dialogueId, destination) {
        fipa = createFipaMessage(targetId) {
            decline = FipaDecline.newBuilder().build()
        }
    }.build()
}

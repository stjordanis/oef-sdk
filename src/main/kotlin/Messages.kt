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
import fetch.oef.pb.AgentOuterClass
import fetch.oef.pb.FipaOuterClass
import java.nio.ByteBuffer

typealias Envelope           = AgentOuterClass.Envelope
typealias Nothing             = AgentOuterClass.Envelope.Nothing
typealias AgentSearch         = AgentOuterClass.AgentSearch
typealias AgentMessagePb      = AgentOuterClass.Agent.Message
typealias FipaMessagePb       = FipaOuterClass.Fipa.Message
typealias AgentMessageBuilder = AgentOuterClass.Agent.Message.Builder
typealias FipaMessageBuilder  = FipaOuterClass.Fipa.Message.Builder
typealias FipaAccept          = FipaOuterClass.Fipa.Accept
typealias FipaDecline         = FipaOuterClass.Fipa.Decline
typealias OEFErrorOperation   = AgentOuterClass.Server.AgentMessage.OEFError.Operation


/**
 * This class repesents the different error types we can get from the OEF.
 */
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


/**
 * To communicate with the OEF we need to send protocol buffer containing an Envelope message. This interface is the base for a classes
 * representing OEF data.
 */
interface BaseMessage {
    fun toEnvelope(): Envelope
}

private fun newEnvelopeBuilder(messageId: Int, agent_uri: OEFURI = OEFURI()) = Envelope.newBuilder()
    .setMsgId(messageId)
    .setAgentUri(agent_uri.toString())


/**
 *  This message is used for registering a new agent  in the Agent Directory of an OEF Node.
 *  The agent is described by a Description message object.
 *
 *  @param msgId identifier of the message
 *  @param agentDescription the agent's description
 */
class RegisterDescription (
    private val msgId: Int,
    private val agentDescription: Description,
    private val uri: OEFURI = OEFURI()
) : BaseMessage {
    /**
     * Wrapps the message to the OEF acceptable Envelope message.
     */
    override fun toEnvelope(): Envelope = newEnvelopeBuilder(msgId)
        .setRegisterDescription(agentDescription.toAgentDescription())
        .build()
}

/**
 * This message is used for registering a new agent in the Service Directory of an OEF Node.
 * The service agent is described by a Description message object.
 *
 *  @param msgId identifier of the message
 *  @param serviceDescription the description of the service provided by the agent
 *  @param uri resource identifier for the service. Using this field one agent is able to register multiple service.
 */
class RegisterService (
    private val msgId: Int,
    private val serviceDescription: Description,
    private val uri: OEFURI = OEFURI()
) : BaseMessage {
    /**
     * Wrapps the message to the OEF acceptable Envelope message.
     */
    override fun toEnvelope(): Envelope = newEnvelopeBuilder(msgId, uri)
        .setRegisterService(serviceDescription.toAgentDescription())
        .build()
}

/**
 * This message is used for unregistering an agent in the Agent Directory of an OEF Node.
 *  @param msgId identifier of the message
 */
class UnregisterDescription (
    private val msgId: Int
) : BaseMessage {
    /**
     * Wrapps the message to the OEF acceptable Envelope message.
     */
    override fun toEnvelope(): Envelope = newEnvelopeBuilder(msgId)
        .setUnregisterDescription(Nothing.newBuilder().build())
        .build()
}

/**
 * This message is used for unregistering a service provided by the agent from the OEF.
 *
 * @param serviceDescription the description of the service provided by the agent
 */
class UnregisterService (
    private val msgId: Int,
    private val serviceDescription: Description,
    private val uri: OEFURI = OEFURI()
): BaseMessage {
    /**
     * Wrapps the message to the OEF acceptable Envelope message.
     */
    override fun toEnvelope(): Envelope = newEnvelopeBuilder(msgId, uri)
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
    /**
     * Wrapps the message to the OEF acceptable Envelope message.
     */
    override fun toEnvelope(): Envelope = newEnvelopeBuilder(searchId)
        .setSearchAgents(
            AgentSearch.newBuilder()
                .setQueryV2(query.toProto())
        )
        .build()
}

/**
 * This message is used for searching agents which provides the queried service. This search will only search on
 * the local node the agent connected to, and not the whole OEF network.
 *
 * @param searchId identifies the search query. This id will be used
 *         by the sender in order to distinguish different incoming search results.
 * @param query i.e. a list of constraints defined over a data model.
 */
class SearchServices (
    private val searchId: Int,
    private val query: Query
) : BaseMessage {
    /**
     * Wrapps the message to the OEF acceptable Envelope message.
     */
    override fun toEnvelope(): Envelope = newEnvelopeBuilder(searchId)
        .setSearchServices(
            AgentSearch.newBuilder()
                .setQueryV2(query.toProto())
        )
        .build()
}

/**
 * This message is used for searching agents in OEF Network.
 *
 * @param searchId identifies the search query. This id will be used by the sender
 *    in order to distinguish different incoming search results.
 * @param query i.e. a list of constraints defined over a data model.
 */
class SearchServicesWide(
    private val searchId: Int,
    private val query: Query
): BaseMessage {
    /**
     * Wrapps the message to the OEF acceptable Envelope message.
     */
    override fun toEnvelope(): Envelope = newEnvelopeBuilder(searchId)
        .setSearchServicesWide(
            AgentSearch.newBuilder()
                .setQueryV2(query.toProto())
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
    context: Context,
    block: AgentMessageBuilder.()->Unit
) =  newEnvelopeBuilder(messageId)
    .setSendMessage(
        AgentMessagePb.newBuilder()
            .setDialogueId(dialogueId)
            .setDestination(destination)
            .setSourceUri(context.sourceURI.toString())
            .setTargetUri(context.targetURI.toString())
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
 *
 * @param dialogueId identifies the dialogue in which the message is sent.
 * @param destination the public key of the recipient of the message.
 * @param message sequence of bytes, that is the content of the message.
 * @param context Context object used to send the message to the agent's specific role (the target agent has multiple
 * services registered, and the source agent is trying to communicate regarding one of the services)
 */
class Message(
    private val messageId: Int,
    private val dialogueId: Int,
    private val destination: String,
    private val message: ByteBuffer,
    private val context: Context = Context()
) : AgentMessage {
    /**
     * Wrapps the message to the OEF acceptable Envelope message.
     */
    override fun toEnvelope(): Envelope = createEnvelopeWithAgentMessageBuilder(messageId, dialogueId, destination, context) {
        content = ByteString.copyFrom(message)
    }.build()
}

/**
 * This message is used to send a `Call For Proposals`.
 *
 * @param messageId unique identifier for a message, given dialogue
 * @param dialogueId identifies the dialogue in which the message is sent
 * @param destination the public key of the recipient of the message
 * @param query describes the resources the sender is interested in
 * @param targetId the identifier of the message to whom this message is targeting, in a given dialogue
 * @param context Context object used to send the message to the agent's specific role (the target agent has multiple
 * services registered, and the source agent is trying to communicate regarding one of the services)
 */
class CFP (
    private val messageId: Int,
    private val dialogueId: Int,
    private val destination: String,
    private val targetId: Int,
    private val query: CFPQuery,
    private val context: Context = Context()
) : AgentMessage {
    /**
     * Wrapps the message to the OEF acceptable Envelope message.
     */
    override fun toEnvelope(): Envelope = createEnvelopeWithAgentMessageBuilder(messageId, dialogueId, destination, context) {
        fipa = createFipaMessage(targetId) {
            cfp = CFPQuery.toProto(query)
        }
    }.build()
}

/**
 * This message is used to send a `Propose`.
 * It contains:
 *      - a dialogue id, that.
 *      - a destination, that is .
 *      - a .
 *      - the message id, that is an unique identifier for a message, given dialogue.
 *      - target, that is, the identifier of the message to whom this message is targeting.
 *
 * @param messageId unique identifier for a message, given dialogue
 * @param dialogueId identifies the dialogue in which the message is sent
 * @param destination the public key of the recipient of the message
 * @param targetId the identifier of the message to whom this message is targeting
 * @param proposals list of proposals describing the resources that the seller proposes
 * @param context Context object used to send the message to the agent's specific role (the target agent has multiple
 * services registered, and the source agent is trying to communicate regarding one of the services)
 */
class Propose (
    private val messageId: Int,
    private val dialogueId: Int,
    private val destination: String,
    private val targetId: Int,
    private val proposals: Proposals,
    private val context: Context = Context()
) : AgentMessage {
    /**
     * Wrapps the message to the OEF acceptable Envelope message.
     */
    override fun toEnvelope(): Envelope = createEnvelopeWithAgentMessageBuilder(messageId, dialogueId, destination, context) {
        fipa = createFipaMessage(targetId) {
            propose = Proposals.toProto(proposals)
        }
    }.build()
}

/**
 *  This message is used to send an `Accept`.
 *
 * @param messageId unique identifier for a message, given dialogue
 * @param dialogueId identifies the dialogue in which the message is sent
 * @param destination the public key of the recipient of the message
 * @param targetId the identifier of the message to whom this message is targeting
 * @param context Context object used to send the message to the agent's specific role (the target agent has multiple
 * services registered, and the source agent is trying to communicate regarding one of the services)
 */
class Accept (
    private val messageId: Int,
    private val dialogueId: Int,
    private val destination: String,
    private val targetId: Int,
    private val context: Context = Context()
) : AgentMessage {
    /**
     * Wrapps the message to the OEF acceptable Envelope message.
     */
    override fun toEnvelope(): Envelope = createEnvelopeWithAgentMessageBuilder(messageId, dialogueId, destination, context) {
        fipa = createFipaMessage(targetId) {
            accept = FipaAccept.newBuilder().build()
        }
    }.build()
}

/**
 * This message is used to send an `Decline`.

 * @param messageId unique identifier for a message, given dialogue
 * @param dialogueId identifies the dialogue in which the message is sent
 * @param destination the public key of the recipient of the message
 * @param targetId the identifier of the message to whom this message is targeting
 * @param context Context object used to send the message to the agent's specific role (the target agent has multiple
 * services registered, and the source agent is trying to communicate regarding one of the services)
 */
class Decline (
    private val messageId: Int,
    private val dialogueId: Int,
    private val destination: String,
    private val targetId: Int,
    private val context: Context = Context()
) : AgentMessage {
    /**
     * Wrapps the message to the OEF acceptable Envelope message.
     */
    override fun toEnvelope(): Envelope = createEnvelopeWithAgentMessageBuilder(messageId, dialogueId, destination, context) {
        fipa = createFipaMessage(targetId) {
            decline = FipaDecline.newBuilder().build()
        }
    }.build()
}

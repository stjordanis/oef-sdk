# -*- coding: utf-8 -*-

# ------------------------------------------------------------------------------
#
#   Copyright 2018 Fetch.AI Limited
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.
#
# ------------------------------------------------------------------------------


"""
oef.proxy
~~~~~~~~~
This module defines the proxies classes used by agents to interact with an OEF Node.
"""

import asyncio
import logging
import struct
from collections import defaultdict
from typing import Optional, Awaitable, Tuple, List, Dict

from protocol.src.proto import agent_pb2
from utils.src.python import uri
from oef.src.python.core import OEFProxy
from oef.src.python.messages import Message, CFP_TYPES, PROPOSE_TYPES, CFP, Propose, Accept, Decline, BaseMessage, \
    AgentMessage, RegisterDescription, RegisterService, UnregisterDescription, \
    UnregisterService, SearchAgents, SearchServices, SearchServicesWide, OEFErrorOperation, SearchResult, \
    OEFErrorMessage, DialogueErrorMessage
from oef.src.python.query import Query
from oef.src.python.schema import Description

logger = logging.getLogger(__name__)


DEFAULT_OEF_NODE_PORT = 3333


class OEFConnectionError(ConnectionError):
    """
    This exception is used whenever an error occurs during the connection to the OEF Node.
    """


class OEFNetworkProxy(OEFProxy):
    """
    Proxy to the functionality of the OEF. Provides functionality for an agent to:
     * Register a description of itself
     * Register its services
     * Locate other agents
     * Locate other services
     * Establish a connection with another agent
    """

    def __init__(self, public_key: str, oef_addr: str, port: int = DEFAULT_OEF_NODE_PORT,
                 loop: asyncio.AbstractEventLoop = None) -> None:
        """
        Initialize the proxy to the OEF Node.
        :param public_key: the public key used in the protocols.
        :param oef_addr: the IP address of the OEF node.
        :param port: port number for the connection.
        :param loop: the event loop.
        """
        super().__init__(public_key, loop=loop)

        self.oef_addr = oef_addr
        self.port = port
        #self._loop = loop if loop is not None else asyncio.get_event_loop()

        # these are setup in _connect_to_server
        self._connection = None
        self._server_reader = None
        self._server_writer = None

    def is_connected(self) -> bool:
        """
        Check if the proxy is currently connected to the OEF Node.
        :return: ``True`` if the proxy is connected, ``False`` otherwise.
        """
        return self._connection is not None

    async def _connect_to_server(self, event_loop) -> Tuple[asyncio.StreamReader, asyncio.StreamWriter]:
        """
        Connect to the OEF Node.
        :param event_loop: the event loop to use for the connection.
        :return: A stream reader and a stream writer for the connection.
        """
        return await asyncio.open_connection(self.oef_addr, self.port, loop=event_loop)

    def _send(self, protobuf_msg) -> None:
        """
        Send a Protobuf message to a previously established connection.
        :param protobuf_msg: the message to be sent
        :return: ``None``
        :raises OEFConnectionError: if the connection has not been established yet.
        """
        if not self.is_connected():
            raise OEFConnectionError("Connection not established yet. Please use 'connect()'.")
        serialized_msg = protobuf_msg.SerializeToString()
        nbytes = struct.pack("I", len(serialized_msg))
        self._server_writer.write(nbytes)
        self._server_writer.write(serialized_msg)

    async def _receive(self):
        """
        Receive a Protobuf message.
        :return: ``None``
        :raises OEFConnectionError: if the connection has not been established yet.
        """
        if not self.is_connected():
            raise OEFConnectionError("Connection not established yet. Please use 'connect()'.")
        nbytes_packed = await self._server_reader.read(len(struct.pack("I", 0)))
        logger.debug("received ${0}".format(nbytes_packed))
        nbytes = struct.unpack("I", nbytes_packed)[0]
        logger.debug("received unpacked ${0}".format(nbytes))
        logger.debug("Preparing to receive ${0} bytes ...".format(nbytes))
        data = b""
        while len(data) < nbytes:
            data += await self._server_reader.read(nbytes - len(data))
            logger.debug("Read bytes: {}".format(len(data)))
        return data

    async def connect(self) -> bool:
        if self.is_connected() and not self._server_writer.transport.is_closing():
            return True

        event_loop = self._loop
        self._connection = await self._connect_to_server(event_loop)
        self._server_reader, self._server_writer = self._connection
        # Step 1: Agent --(ID)--> OEFCore
        pb_public_key = agent_pb2.Agent.Server.ID()
        pb_public_key.public_key = self.public_key
        self._send(pb_public_key)
        # Step 2: OEFCore --(Phrase)--> Agent
        data = await self._receive()
        pb_phrase = agent_pb2.Server.Phrase()
        pb_phrase.ParseFromString(data)
        case = pb_phrase.WhichOneof("payload")
        if case == "failure":
            return False
        # Step 3: Agent --(Answer)--> OEFCore
        pb_answer = agent_pb2.Agent.Server.Answer()
        pb_answer.answer = pb_phrase.phrase[::-1]
        pb_answer.capability_bits.will_heartbeat = True

        self._send(pb_answer)
        # Step 4: OEFCore --(Connected)--> Agent
        data = await self._receive()
        pb_status = agent_pb2.Server.Connected()
        pb_status.ParseFromString(data)
        return pb_status.status

    def register_agent(self, msg_id: int, agent_description: Description):
        msg = RegisterDescription(msg_id, agent_description)
        self._send(msg.to_pb())

    def register_service(self, msg_id: int, service_description: Description, service_id: str = ""):
        uri_builder = uri.OEFURI.Builder()
        uri_builder.agentKey(self._public_key)
        uri_builder.agentAlias(service_id)
        msg = RegisterService(msg_id, service_description, uri_builder.build())
        self._send(msg.to_pb())

    def unregister_agent(self, msg_id: int):
        msg = UnregisterDescription(msg_id)
        self._send(msg.to_pb())

    def unregister_service(self, msg_id: int, service_description: Description, service_id: str = ""):
        uri_builder = uri.OEFURI.Builder()
        uri_builder.agentKey(self._public_key)
        uri_builder.agentAlias(service_id)
        msg = UnregisterService(msg_id, service_description, uri_builder.build())
        self._send(msg.to_pb())

    def search_agents(self, search_id: int, query: Query) -> None:
        msg = SearchAgents(search_id, query)
        self._send(msg.to_pb())

    def search_services(self, search_id: int, query: Query) -> None:
        msg = SearchServices(search_id, query)
        self._send(msg.to_pb())

    def search_services_wide(self, search_id: int, query: Query) -> None:
        msg = SearchServicesWide(search_id, query)
        self._send(msg.to_pb())

    def send_message(self, msg_id: int, dialogue_id: int, destination: str, msg: bytes,
                     context=uri.Context()) -> None:
        msg = Message(msg_id, dialogue_id, destination, msg, context)
        self._send(msg.to_pb())

    def send_cfp(self, msg_id: int, dialogue_id: int, destination: str, target: int, query: CFP_TYPES,
                 context=uri.Context()):
        msg = CFP(msg_id, dialogue_id, destination, target, query, context)
        self._send(msg.to_pb())

    def send_propose(self, msg_id: int, dialogue_id: int, destination: str, target: int, proposals: PROPOSE_TYPES,
                     context=uri.Context()):
        msg = Propose(msg_id, dialogue_id, destination, target, proposals, context)
        self._send(msg.to_pb())

    def send_accept(self, msg_id: int, dialogue_id: int, destination: str, target: int, context=uri.Context()):
        msg = Accept(msg_id, dialogue_id, destination, target, context)
        self._send(msg.to_pb())

    def send_decline(self, msg_id: int, dialogue_id: int, destination: str, target: int, context=uri.Context()):
        msg = Decline(msg_id, dialogue_id, destination, target, context)
        self._send(msg.to_pb())

    async def stop(self) -> None:
        """
        Tear down resources associated with this Proxy, i.e. the writing connection with the server.
        """
        await self._server_writer.drain()
        self._server_writer.close()
        self._server_writer = None
        self._server_reader = None
        self._connection = None

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
oef.agents
~~~~~~~~~~
This module contains the base class for implementing agents.
"""
import asyncio

import logging
from abc import ABC
from typing import List, Optional
import re

from oef.src.python.core import OEFProxy, AgentInterface
from oef.src.python.messages import OEFErrorOperation
from oef.src.python.proxy import OEFNetworkProxy, PROPOSE_TYPES, CFP_TYPES, OEFConnectionError
from oef.src.python.Query import Query, SearchResultItem
from oef.src.python.schema import Description
from utils.src.python import uri

logger = logging.getLogger(__name__)


def _warning_not_implemented_method(method_name: str) -> None:
    """
    Raise a warning if a method has not been implemented.
    :param method_name: the method name to report in the warning
    :return: ``None``
    """
    logger.warning("You should implement {} in your OEFAgent class.".format(method_name))


class Agent(AgentInterface, ABC):
    """
    The base class for OEF Agents.
    Extend this class to implement the callback methods defined in
    :class:`~oef.core.DialogueInterface` and :class:`~oef.core.ConnectionInterface`.
    In this way you can program the behaviour of the agent when it's running.
    """

    @property
    def public_key(self) -> str:
        """
        The public key that identifies the agent in the OEF.
        :return: the public key.
        """
        return self._oef_proxy.public_key

    def __init__(self, oef_proxy: OEFProxy):
        """
        Initialize the OEF Agent.
        :param oef_proxy: the proxy for an OEF Node.
        """

        self._oef_proxy = oef_proxy
        self._loop = self._oef_proxy._loop
        self._task = None

    def getContext(self, message_id: int, dialogue_id: int, origin: str):
        return self._oef_proxy.getContext(message_id, dialogue_id, origin)


    def run(self) -> None:
        """
        Run the agent synchronously. That is, until :func:`~oef.agents.Agent.stop` is not called.
        :return: ``None``
        """
        self._loop.run_until_complete(self.async_run())

    async def async_run(self) -> None:
        """
        Run the agent asynchronously.
        :return: ``None``
        """
        if self._task:
            logger.warning("Agent {} already scheduled for running.".format(self.public_key))
            return
        self._oef_proxy._active_loop = True
        try:
            self._task = asyncio.ensure_future(self._oef_proxy.loop(self), loop=self._loop)
            await self._task
        except asyncio.CancelledError:
            pass

    def halt_loop(self):
        self._loop.stop()

    def stop(self) -> None:
        """
        Stop the agent. Specifically, if :func:`~oef.agents.Agent.run` or :func:`~oef.agents.Agent.async_run`
        have been called, then this method will cancel the previously instantiated task.
        The task that manages the agent-loop is hence scheduled for cancellation.
        :return: ``None``
        """
        if self._task:
            try:
                self._task.cancel()
                self._task = None
                self._oef_proxy._active_loop = False
            except asyncio.CancelledError:
                pass
        else:
            raise Exception("NO TASK TO CANCEL")

    def connect(self) -> bool:
        """
        Connect to the OEF Node.
        :return: True if the connection has been established successfully, False otherwise.
        """
        return self._loop.run_until_complete(self.async_connect())

    async def async_connect(self) -> bool:
        """
        The asynchronous counterpart of :func:`~oef.agents.Agent.connect`.
        :return: True if the connection has been established successfully, False otherwise.
        """
        logger.debug("{}: Connecting...".format(self.public_key))
        status = await self._oef_proxy.connect()
        if status:
            logger.debug("{}: Connection established.".format(self.public_key))
        else:
            raise OEFConnectionError("Public key already in use.")
        return status

    def disconnect(self) -> None:
        """
        Disconnect from the OEF Node.
        :return: ``None``
        """
        return self._loop.run_until_complete(self.async_disconnect())

    async def async_disconnect(self) -> None:
        """
        The asynchronous counterpart of :func:`~oef.agents.Agent.disconnect`.
        :return: ``None``
        """
        if self._oef_proxy.is_connected():
            await self._oef_proxy.stop()

    def register_agent(self, msg_id: int, agent_description: Description) -> None:
        """Register an agent. See :func:`~oef.core.OEFCoreInterface.register_agent`."""
        self._oef_proxy.register_agent(msg_id, agent_description)

    def unregister_agent(self, msg_id: int) -> None:
        """Unregister an agent. See :func:`~oef.core.OEFCoreInterface.unregister_agent`."""
        self._oef_proxy.unregister_agent(msg_id)

    def register_service(self, msg_id: int, service_description: Description, service_id: str = "") -> None:
        """Unregister a service. See :func:`~oef.core.OEFCoreInterface.register_service`."""
        self._oef_proxy.register_service(msg_id, service_description, service_id)

    def unregister_service(self, msg_id: int, service_description: Description, service_id: str = "") -> None:
        """Unregister a service. See :func:`~oef.core.OEFCoreInterface.unregister_service`."""
        self._oef_proxy.unregister_service(msg_id, service_description, service_id)

    def search_agents(self, search_id: int, query: Query) -> None:
        """Search agents. See :func:`~oef.core.OEFCoreInterface.search_agents`."""
        self._oef_proxy.search_agents(search_id, query)

    def search_services(self, search_id: int, query: Query) -> None:
        """Search services. See :func:`~oef.core.OEFCoreInterface.search_services`."""
        self._oef_proxy.search_services(search_id, query)

    def search_services_wide(self, search_id: int, query: Query) -> None:
        """Search services widely. See :func:`~oef.core.OEFCoreInterface.search_services_wide`."""
        self._oef_proxy.search_services_wide(search_id, query)

    def send_message(self, msg_id: int, dialogue_id: int, destination: str, msg: bytes, context=uri.Context()) -> None:
        """Send a simple message. See :func:`~oef.core.OEFCoreInterface.send_message`."""
        logger.debug("Agent {}: msg_id={}, dialogue_id={}, destination={}, msg={}"
                     .format(self.public_key, msg_id, dialogue_id, destination, msg))
        self._oef_proxy.send_message(msg_id, dialogue_id, destination, msg, context)

    def send_cfp(self, msg_id: int, dialogue_id: int, destination: str, target: int, query: CFP_TYPES, context=uri.Context()) -> None:
        """Send a CFP. See :func:`~oef.core.OEFCoreInterface.send_cfp`."""
        logger.debug("Agent {}: msg_id={}, dialogue_id={}, destination={}, target={}, query={}"
                     .format(self.public_key, dialogue_id, destination, query, msg_id, target))
        self._oef_proxy.send_cfp(msg_id, dialogue_id, destination, target, query, context)

    def send_propose(self, msg_id: int, dialogue_id: int, destination: str, target: int,
                     proposals: PROPOSE_TYPES, context=uri.Context()) -> None:
        """Send a Propose. See :func:`~oef.core.OEFCoreInterface.send_propose`."""
        logger.debug("Agent {}: msg_id={}, dialogue_id={}, destination={}, target={}, proposals={}"
                     .format(self.public_key, msg_id, dialogue_id, destination, target, proposals))
        self._oef_proxy.send_propose(msg_id, dialogue_id, destination, target, proposals, context)

    def send_accept(self, msg_id: int, dialogue_id: int, destination: str, target: int, context=uri.Context()) -> None:
        """Send an Accept. See :func:`~oef.core.OEFCoreInterface.send_accept`."""
        logger.debug("Agent {}: dialogue_id={}, destination={}, msg_id={}, target={}"
                     .format(self.public_key, msg_id, dialogue_id, destination, target))
        self._oef_proxy.send_accept(msg_id, dialogue_id, destination, target, context)

    def send_decline(self, msg_id: int, dialogue_id: int, destination: str, target: int, context=uri.Context()) -> None:
        """Send a Decline. See :func:`~oef.core.OEFCoreInterface.send_decline`."""
        logger.debug("Agent {}: dialogue_id={}, destination={}, msg_id={}, target={}"
                     .format(self.public_key, msg_id, dialogue_id, destination, target))
        self._oef_proxy.send_decline(msg_id, dialogue_id, destination, target, context)

    def on_message(self, msg_id: int, dialogue_id: int, origin: str, content: bytes):
        logger.debug("on_message: msg_id={}, dialogue_id={}, origin={}, content={}"
                     .format(msg_id, dialogue_id, origin, content))
        _warning_not_implemented_method(self.on_message.__name__)

    def on_cfp(self, msg_id: int, dialogue_id: int, origin: str, target: int, query: CFP_TYPES):
        logger.debug("on_cfp: msg_id={}, dialogue_id={}, origin={}, target={}, query={}"
                     .format(msg_id, dialogue_id, origin, target, query))
        _warning_not_implemented_method(self.on_cfp.__name__)

    def on_propose(self, msg_id: int, dialogue_id: int, origin: str, target: int, proposals: PROPOSE_TYPES):
        logger.debug("on_propose: msg_id={}, dialogue_id={}, origin={}, target={}, proposals={}"
                     .format(msg_id, dialogue_id, origin, target, proposals))
        _warning_not_implemented_method(self.on_propose.__name__)

    def on_accept(self, msg_id: int, dialogue_id: int, origin: str, target: int):
        logger.debug("on_accept: msg_id={}, dialogue_id={}, origin={}, target={}"
                     .format(msg_id, dialogue_id, origin, target))
        _warning_not_implemented_method(self.on_accept.__name__)

    def on_decline(self, msg_id: int, dialogue_id: int, origin: str, target: int):
        logger.debug("on_accept: msg_id={}, dialogue_id={}, origin={}, target={}"
                     .format(msg_id, dialogue_id, origin, target))
        _warning_not_implemented_method(self.on_decline.__name__)

    def on_oef_error(self, answer_id: int, operation: OEFErrorOperation):
        logger.debug("on_oef_error: answer_id={}, operation={}".format(answer_id, operation))
        _warning_not_implemented_method(self.on_oef_error.__name__)

    def on_dialogue_error(self, answer_id: int, dialogue_id: int, origin: str):
        logger.debug("on_dialogue_error: answer_id={}, dialogue_id={}, origin={}"
                     .format(answer_id, dialogue_id, origin))
        _warning_not_implemented_method(self.on_dialogue_error.__name__)

    def on_search_result(self, search_id: int, agents: List[str]):
        logger.debug("on_search_result: search_id={}, agents={}".format(search_id, agents))
        _warning_not_implemented_method(self.on_search_result.__name__)

    def on_search_result_wide(self, search_id: int, agents: List[SearchResultItem]):
        logger.debug("on_search_result_wide: search_id={}, agents={}".format(search_id, agents))
        _warning_not_implemented_method(self.on_search_result_wide.__name__)


class InvalidPublicKeyException(Exception):
    pass


class OEFAgent(Agent):
    """
    Agent that interacts with an OEFNode on the network.
    It provides a nicer constructor that does not require to instantiate :class:`~oef.proxy.OEFLocalProxy` explicitly.
    """

    def __init__(self, public_key: str, oef_addr: str, oef_port: int = 3333,
                 loop: Optional[asyncio.AbstractEventLoop] = None):
        """
        Initialize an OEF network agent.
        :param public_key: the public key (identifier) of the agent
        :param oef_addr: the IP address of the OEF Node.
        :param oef_port: the port for the connection.
        :param loop: the event loop.
        """
        if not self.validate_pubkey(public_key):
            raise InvalidPublicKeyException("Public key contains invalid characters! Only base58 characters supported!")
        self._oef_addr = oef_addr
        self._oef_port = oef_port
        super().__init__(OEFNetworkProxy(public_key, str(self._oef_addr), self._oef_port, loop=loop))

    def validate_pubkey(self, public_key: str):
        """
        Validates the given public key. A valid public key only contains Base58 characters, which consist of
        alphanumeric characters, except the following characters: 0 (zero), O (capital o), I (capital i)
        and l (lower case L).
        :param public_key: the public key as a string
        :return: boolean
        """
        return re.search(r"^[a-km-zA-HJ-NP-Z1-9]+$", public_key)

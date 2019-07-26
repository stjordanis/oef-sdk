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

import kotlin.coroutines.CoroutineContext


/**
 * The base class for OEF Agents.
 * Extend this class to implement the callback methods defined in [AgentMessageHandlerInterface] and [OEFConnectionInterface].
 * In this way you can program the behaviour of the agent when it's running.
 */
abstract class Agent (
    private val proxy: OEFProxy
) : OEFProxyInterface by proxy,
    OEFDelayInterface by proxy,
    AgentCommunicationHandlerInterface,
    OEFConnectionInterface {

    /**
     * Connect to the OEF Node.
     * @return True if the connection has been established successfully, False otherwise.
     */
    override fun connect(): Boolean {
        proxy.setAgent(this)
        return proxy.connect()
    }

    /**
     * Disconnect from the OEF Node.
     * @return None
     */
    override fun stop() {
        proxy.stop()
    }
}


/**
 * Agent that interacts with an OEFNode on the network.
 */
abstract class OEFAgent @JvmOverloads constructor(
    publicKey: String,
    oefAddress: String,
    port: Int,
    handlerContext: CoroutineContext? = null
) : Agent(OEFNetworkProxy(publicKey, oefAddress, port, handlerContext))
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

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import assertk.assert
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import fetch.oef.pb.AgentOuterClass

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestOEFProxy {

    companion object {
        val log by logger()
    }

    @Test
    fun connect() {
        val proxy = OEFNetworkProxy("12345", "127.0.0.1")
        assert(proxy.connect()).isTrue()
        proxy.stop()
    }

    class LogAgent(proxy: OEFProxy, private val foundAgents: MutableList<String>?=null) : Agent(proxy) {
        override fun onError(
            operation: AgentOuterClass.Server.AgentMessage.Error.Operation,
            dialogueId: Int,
            messageId: Int
        ) {
            log.error("Error: dialogueId=$dialogueId, messageId=$messageId, operation=${operation.name}")
        }

        override fun onSearchResult(searchId: Int, agents: List<String>) {
            log.info("Got search result: searchId: $searchId, agents: ${agents.size}")
            foundAgents?.addAll(0, agents)
        }

        override fun onMessage(dialogueId: Int, origin: String, conent: ByteArray) {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun onCFP(dialogueId: Int, origin: String, messageId: Int, target: Int, query: CFPQuery) {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun onPropose(dialogueId: Int, origin: String, messageId: Int, target: Int, proposals: Proposals) {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun onAccept(dialogueId: Int, origin: String, messageId: Int, target: Int) {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun onDecline(dialogueId: Int, origin: String, messageId: Int, target: Int) {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }
    }

    @Test
    fun `Register agent with OEF node`(){
        val proxy = OEFNetworkProxy("123456", "127.0.0.1")
        val agent = LogAgent(proxy)
        assert(agent.connect()).isTrue()

        agent.registerAgent(Description())

        val proxy2 = OEFNetworkProxy("1234567", "127.0.0.1")
        val foundAgents = mutableListOf<String>()
        val agent2 = LogAgent(proxy2,foundAgents)
        assert(agent2.connect()).isTrue()
        agent.registerAgent(Description())
        agent2.searchAgents(1, Query())

        Thread.sleep(1000)

        assert(foundAgents.find {
            it=="123456"
        }).isNotNull()

        agent.unregisterAgent()

        Thread.sleep(1000)
        agent.close()
        agent2.close()
    }
}
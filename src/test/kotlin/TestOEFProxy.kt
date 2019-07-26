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

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import assertk.assert
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.nio.ByteBuffer

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestOEFProxy {

    companion object {
        val log by logger()
        val core_ip: String = "127.0.0.1"
        val core_port: Int = 10000
    }

    @Test
    fun connect() {
        val proxy = OEFNetworkProxy("12345", core_ip, core_port)
        assert(proxy.connect()).isTrue()
        proxy.stop()
    }

    class LogAgent(proxy: OEFProxy, private val foundAgents: MutableList<String>?=null) : Agent(proxy) {
        override fun onOEFError(messageId: Int, error: OEFError) {
            log.error("Error: messageId=$messageId, operation=${error.name}")
        }

        override fun onDialogueError(messageId: Int, dialogueId: Int) {
            log.error("Error: dialogueId=$dialogueId, messageId=$messageId")
        }

        override fun onSearchResult(searchId: Int, agents: List<String>) {
            log.info("Got search result: searchId: $searchId, agents: ${agents.size}")
            foundAgents?.addAll(0, agents)
        }

        override fun onSearchResultWide(searchId: Int, result: List<SearchResultItem>) {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun onMessage(answerId: Int, dialogueId: Int, origin: String, content: ByteBuffer) {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun onCFP(answerId: Int, dialogueId: Int, origin: String, target: Int, query: CFPQuery) {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun onPropose(answerId: Int, dialogueId: Int, origin: String, target: Int, proposals: Proposals) {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun onAccept(answerId: Int, dialogueId: Int, origin: String, target: Int) {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun onDecline(answerId: Int, dialogueId: Int, origin: String, target: Int) {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }
    }

    @Test
    fun `Register agent with OEF node`() = runBlocking {
        val proxy = OEFNetworkProxy("123456", core_ip, core_port)
        val agent = LogAgent(proxy)
        assert(agent.connect()).isTrue()

        val mouseSpeed = AttributeSchema("mouse_speed",   AttributeSchema.Type.BOOL, true, "Weather book mouse")
        val mouseDataModel = DataModel(
            "mouse_data",
            listOf(mouseSpeed),
            "Experimental mouse data, who read books and tells the weather"
        )
        val mouseServiceDescription = Description(
            listOf(
                descriptionPair("mouse_speed", false)
            ),
            mouseDataModel
        )

        agent.registerAgent(0, mouseServiceDescription)
        delay(1000)

        val proxy2 = OEFNetworkProxy("1234567", core_ip, core_port)
        val foundAgents = mutableListOf<String>()
        val agent2 = LogAgent(proxy2,foundAgents)
        assert(agent2.connect()).isTrue()

        agent2.searchAgents(1, Query(listOf<Constraint>(), mouseDataModel))

        delay(2000)
        println(foundAgents)

        assert(foundAgents.find {
            it=="123456"
        }).isNotNull()

        agent.unregisterAgent(1)
        agent2.unregisterAgent(1)

        agent.close()
        agent2.close()
    }
}
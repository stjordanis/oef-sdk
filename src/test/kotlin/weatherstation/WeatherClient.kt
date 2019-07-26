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

package ai.fetch.oef.weatherstation

import ai.fetch.oef.*
import kotlinx.coroutines.runBlocking
import java.nio.ByteBuffer


class WeatherClient (
    private val publicKey: String,
    oefAddress: String,
    oefPort: Int
) : OEFAgent(publicKey, oefAddress, oefPort) {

    companion object {
        val log by logger()
    }

    override fun onOEFError(messageId: Int, error: OEFError) {
        log.error("OEFError: msg_id: $messageId, error: $error")
    }

    override fun onDialogueError(messageId: Int, dialogueId: Int) {
        log.error("Dialogue error: $messageId, $dialogueId")
    }

    override fun onSearchResult(searchId: Int, agents: List<String>) {
        log.info("Found agents: $agents, sending cfps...")
        var i=10
        for(agent in agents){
            //we need to do this if we get agent_key/agent_alias and we don't want to lose agent_alias
            val context = Context()
            context.forAgent(agent, publicKey, true) // agent alias will be the same
            log.info("TARGET: ${context.targetURI}")
            sendCFP(i++, 0, agent, 0, cfpQueryFrom(Query()))
        }
    }

    override fun onSearchResultWide(searchId: Int, result: List<SearchResultItem>) {
        log.info("Got wide search result: $result")
    }

    override fun onMessage(answerId: Int, dialogueId: Int, origin: String, content: ByteBuffer) {
        val data = messageCoder.decode(content).toString()
        val context = getContext(answerId, dialogueId, origin)
        log.info("Received message from $origin (source=${context.sourceURI}, target = ${context.targetURI}). Message: $data")
    }

    override fun onCFP(answerId: Int, dialogueId: Int, origin: String, target: Int, query: CFPQuery) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onPropose(answerId: Int, dialogueId: Int, origin: String, target: Int, proposals: Proposals) {
        val context = getContext(answerId, dialogueId, origin)
        log.info("Received propose from agent $origin (source=${context.sourceURI}, target=${context.targetURI})")
        proposals as Proposals.TDescriptions
        for (i in 0 until proposals.value.size) {
            proposals.value[i].attributeValues.forEach {
                log.info("Proposal $i: ${it.name} ${it.value}")
            }
        }
        log.info("Accepting proposal")
        context.swap()
        sendAccept(answerId, dialogueId, origin, answerId+1, context)
    }

    override fun onAccept(answerId: Int, dialogueId: Int, origin: String, target: Int) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onDecline(answerId: Int, dialogueId: Int, origin: String, target: Int) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

fun main(args: Array<String>)  = runBlocking<Unit> {
    val agent = WeatherClient("weather_client", "127.0.0.1", 10002)

    if (!agent.connect()){
        return@runBlocking
    }

    val query = Query(
        listOf(
            Constraint(WeatherAttr.Temperature.name, Relation.EQ(true)),
            Constraint(WeatherAttr.AirPressure.name, Relation.EQ(true)),
            Constraint(WeatherAttr.Humidity.name,    Relation.EQ(true))


         //   ,Constraint("location", Distance(Location(435.4, 425.3), 200000.0))
        ),
        WeatherDataModel
    )

    agent.searchServices(0, query)

    Runtime.getRuntime().addShutdownHook(Thread {
        agent.stop()
    })

    agent.delayUntilStopped()
}
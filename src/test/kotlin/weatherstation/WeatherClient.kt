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

package fetch.oef.sdk.kotlin.weatherstation

import fetch.oef.pb.AgentOuterClass
import fetch.oef.sdk.kotlin.*
import kotlinx.coroutines.runBlocking
import java.nio.ByteBuffer


class WeatherClient (
    publicKey: String,
    oefAddress: String,
    oefPort: Int
) : OEFAgent(publicKey, oefAddress, oefPort) {

    companion object {
        val log by logger()
    }

    override fun onError(
        operation: AgentOuterClass.Server.AgentMessage.Error.Operation,
        dialogueId: Int,
        messageId: Int
    ) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onSearchResult(searchId: Int, agents: List<String>) {
        log.info("Found agents: $agents, sending cfps...")
        for(agent in agents){
            sendCFP(0, agent, cfpQueryFrom(Query()))
        }
    }

    override fun onMessage(dialogueId: Int, origin: String, content: ByteBuffer) {
       val data = messageCoder.decode(content).toString()
        log.info("Received message from $origin. Message: $data")
    }

    override fun onCFP(dialogueId: Int, origin: String, messageId: Int, target: Int, query: CFPQuery) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onPropose(dialogueId: Int, origin: String, messageId: Int, target: Int, proposals: Proposals) {
        log.info("Received propose from agent $origin")
        proposals as Proposals.TDescriptions
        for (i in 0 until proposals.value.size) {
            proposals.value[i].attributeValues.forEach {
                log.info("Proposal $i: ${it.name} ${it.value}")
            }
        }
        log.info("Accepting proposal")
        sendAccept(dialogueId, origin, messageId+1, messageId)
    }

    override fun onAccept(dialogueId: Int, origin: String, messageId: Int, target: Int) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onDecline(dialogueId: Int, origin: String, messageId: Int, target: Int) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

fun main(args: Array<String>)  = runBlocking<Unit> {
    val agent = WeatherClient("weather_client", "127.0.0.1", 3333)
    agent.connect()

    val query = Query(
        listOf(
            Constraint(WeatherAttr.Temperature, Relation.EQ(true)),
            Constraint(WeatherAttr.AirPressure, Relation.EQ(true)),
            Constraint(WeatherAttr.Humidity,    Relation.EQ(true))
        ),
        WeatherDataModel
    )

    agent.searchServices(0, query)

    Runtime.getRuntime().addShutdownHook(Thread {
        agent.stop()
    })

    agent.delayUntilStopped()
}
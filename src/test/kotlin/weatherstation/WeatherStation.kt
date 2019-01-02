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
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.nio.ByteBuffer


class WeatherStation (
    publicKey: String,
    oefAddress: String,
    oefPort: Int
) : OEFAgent(publicKey, oefAddress, oefPort) {

    companion object {
        val log by logger()
        val weatherServiceDescription = Description(
            listOf(
                descriptionPair("wind_speed", false),
                descriptionPair("temperature", true),
                descriptionPair("air_pressure", true),
                descriptionPair("humidity", true)
            ),
            WeatherDataModel
        )
    }
    override fun onError(
        operation: AgentOuterClass.Server.AgentMessage.Error.Operation,
        dialogueId: Int,
        messageId: Int
    ) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onSearchResult(searchId: Int, agents: List<String>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onMessage(dialogueId: Int, origin: String, content: ByteBuffer) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onCFP(dialogueId: Int, origin: String, messageId: Int, target: Int, query: CFPQuery) {
        log.info("Received CFP from $origin")

        val description = descriptionOf(descriptionPair("price", 50))
        val proposals   = proposalsFrom(description)

        sendPropose(dialogueId, origin, proposals, messageId+1, target+1)
    }

    override fun onPropose(dialogueId: Int, origin: String, messageId: Int, target: Int, proposals: Proposals) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onAccept(dialogueId: Int, origin: String, messageId: Int, target: Int) {
        log.info("Received accept from $origin")

        sendMessage(dialogueId, origin, messageCoder.encode("temperature: 15.0"))
        sendMessage(dialogueId, origin, messageCoder.encode("humidity: 0.7"))
        sendMessage(dialogueId, origin, messageCoder.encode("air_pressure: 1019.0"))
    }

    override fun onDecline(dialogueId: Int, origin: String, messageId: Int, target: Int) {
        log.info("Received decline from $origin")
    }
}

fun main(args: Array<String>) = runBlocking<Unit> {
    val agent = WeatherStation("weather_station", "127.0.0.1", 3333)
    agent.connect()

    agent.registerService(WeatherStation.weatherServiceDescription)

    Runtime.getRuntime().addShutdownHook(Thread {
        agent.stop()
    })

    // this will delay the coroutine until the stop method is called (or the job is canceled)
    // because we are in runBlocking this will block till we stop the agent
    agent.delayUntilStopped()
}
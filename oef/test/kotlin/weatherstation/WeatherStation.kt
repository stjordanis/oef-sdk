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

    override fun onOEFError(messageId: Int, error: OEFError) {
        log.error("OEFError: msg_id: $messageId, error: $error")
    }

    override fun onDialogueError(messageId: Int, dialogueId: Int) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onSearchResult(searchId: Int, agents: List<String>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onSearchResultWide(searchId: Int, result: List<SearchResultItem>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onCFP(answerId: Int, dialogueId: Int, origin: String, target: Int, query: CFPQuery) {
        val context = getContext(answerId, dialogueId, origin)
        log.info("Received CFP from $origin, for service: ${context.serviceId}")

        val description = descriptionOf(descriptionPair("price", 50))
        val proposals   = proposalsFrom(description)

        context.swap() // important, if we send to an agent via context, we need to call swap once before send
        sendPropose(answerId+1, dialogueId, origin, target+1,  proposals, context)
    }

    override fun onPropose(answerId: Int, dialogueId: Int, origin: String, target: Int, proposals: Proposals) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onAccept(answerId: Int, dialogueId: Int, origin: String, target: Int) {
        val context = getContext(answerId, dialogueId, origin)
        log.info("Received accept from $origin for service ${context.serviceId}")

        context.swap() // important, if we send to an agent via context, we need to call swap once before send
        sendMessage(answerId+1, dialogueId, origin, messageCoder.encode("temperature: 15.0"), context)
        sendMessage(answerId+1, dialogueId, origin, messageCoder.encode("humidity: 0.7"), context)
        sendMessage(answerId+1, dialogueId, origin, messageCoder.encode("air_pressure: 1019.0"), context)
    }

    override fun onDecline(answerId: Int, dialogueId: Int, origin: String, target: Int) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onMessage(answerId: Int, dialogueId: Int, origin: String, content: ByteBuffer) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

fun main(args: Array<String>) = runBlocking<Unit> {
    val agent = WeatherStation("weather_station", "127.0.0.1", 10000)
    agent.connect()

    agent.registerService(0, WeatherStation.weatherServiceDescription)
    agent.registerService(1, WeatherStation.weatherServiceDescription, "second")


    Runtime.getRuntime().addShutdownHook(Thread {
        agent.stop()
    })

    // this will delay the coroutine until the stop method is called (or the job is canceled)
    // because we are in runBlocking this will block till we stop the agent
    agent.delayUntilStopped()
}
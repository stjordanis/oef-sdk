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


class OEFURI {
    var protocol: String = "tcp"
    var coreURI: String = ""
    var coreKey: String = ""
    var namespaces: ArrayList<String> = arrayListOf()
    var agentKey: String = ""
    var agentAlias: String = ""
    var empty = true

    companion object {
        val log by logger()
    }

    override fun toString() = if (empty) {
        ""
    } else {
        "$protocol://$coreURI/$coreKey/${namespaces.joinToString("/")}/$agentKey/$agentAlias"
    }

    fun parse(uri: String) = uri.split("/").let {
        if (it.size < 7) {
            log.warn("OEFURI parse failed! Invalid URI: $uri")
            return@let
        }
        empty = false
        protocol =  it[0].replace(":", "")
        coreURI = it[2]
        coreKey = it[3]
        val len = it.size
        agentAlias = it[len-1]
        agentKey = it[len-2]
        for(i in 4.until(len-2)) {
            namespaces.add(it[i])
        }
    }

    fun parseAgent(agent: String) {
        empty=false
        if (agent.find{it=='/'} == null) {
            agentKey = agent
            return
        }
        agent.split("/").also {
            if (it.size!=2){
                log.warn("Got invalid agent key: $agent (${it.size})")
                return@also
            }
            agentKey = it[0]
            agentAlias = it[1]
        }
    }


    class Builder (
        private var uri: OEFURI = OEFURI()
    ){

        init {
            uri.empty = false
        }

        fun protocol(protocol: String): Builder {
            uri.protocol = protocol
            return this
        }

        fun coreAddress(host: String, port: Int): Builder {
            uri.coreURI = "$host:$port"
            return this
        }

        fun coreKey(key: String): Builder {
            uri.coreKey = key
            return this
        }

        fun agentKey(agent_key: String): Builder {
            uri.agentKey = agent_key
            return this
        }

        fun addNamespace(namespace: String): Builder{
            uri.namespaces.add(namespace)
            return this
        }

        fun agentAlias(agent_alias: String): Builder {
            uri.agentAlias = agent_alias
            return this
        }

        fun build(): OEFURI = uri
    }

}
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

package ai.fetch.oef.examples.java;

import ai.fetch.oef.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.text.MessageFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static ai.fetch.oef.InterfacesKt.proposalsFrom;
import static ai.fetch.oef.SchemaKt.descriptionOf;
import static ai.fetch.oef.SchemaKt.descriptionPair;


public class WeatherStation extends OEFAgent {

    private static Logger log = LogManager.getLogger(WeatherStation.class.getName());
    private static Description weatherServiceDescription = new Description(
            List.of(descriptionPair("wind_speed", false),
                    descriptionPair("temperature", true),
                    descriptionPair("air_pressure", true),
                    descriptionPair("humidity", true)
            ), WeatherContract.WeatherDataModel
    );

    private WeatherStation(String publicKey, String oefAddress, int oefPort) {
        super(publicKey, oefAddress, oefPort);
    }

    @Override
    public void onAccept(int answerId, int dialogueId, @NotNull String origin, int target) {
        log.info(MessageFormat.format("Received accept from {0}", origin));

        Context context = getContext(answerId, dialogueId, origin);
        context.swap();
        sendMessage(answerId+1, dialogueId, origin, WeatherContract.messageCoder.encode("temperature: 15.0"), context);
        sendMessage(answerId+1, dialogueId, origin, WeatherContract.messageCoder.encode("humidity: 0.7"), context);
        sendMessage(answerId+1, dialogueId, origin, WeatherContract.messageCoder.encode("air_pressure: 1019.0"), context);
    }

    @Override
    public void onCFP(int answerId, int dialogueId, @NotNull String origin, int target, @NotNull CFPQuery cfpQuery) {
        log.info(MessageFormat.format("Received CFP from {0}", origin));

        Description description = descriptionOf(descriptionPair("price", 50));
        Proposals proposals = proposalsFrom(description);
        Context context = getContext(answerId, dialogueId, origin);
        context.swap();
        sendPropose(answerId + 1, dialogueId, origin, target + 1, proposals, context);
    }

    @Override
    public void onDecline(int i, int i1, @NotNull String s, int i2) {

    }

    @Override
    public void onMessage(int i, int i1, @NotNull String s, @NotNull ByteBuffer byteBuffer) {

    }

    @Override
    public void onPropose(int i, int i1, @NotNull String s, int i2, @NotNull Proposals proposals) {

    }

    @Override
    public void onDialogueError(int i, int i1) {

    }

    @Override
    public void onOEFError(int i, @NotNull OEFError oefError) {
        log.error(MessageFormat.format("OEFError: msg_id: {0}, error: {1}", i, oefError));
    }

    @Override
    public void onSearchResult(int i, @NotNull List<String> list) {

    }

    @Override
    public void onSearchResultWide(int i, @NotNull List<SearchResultItem> list) {
    }

    public static void main(String[] args) {
        WeatherStation agent = new WeatherStation("weather_station", "127.0.0.1", 10000);

        if (!agent.connect()){
            return;
        }

        agent.registerService(0, WeatherStation.weatherServiceDescription, "");


        AtomicBoolean isActive = new AtomicBoolean(true);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            agent.stop();
            isActive.set(false);
        }));

        // this will delay the coroutine until the stop method is called (or the job is canceled)
        // because we are in runBlocking this will block till we stop the agent
        while (isActive.get()){
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ex) {
                isActive.set(false);
            }
        }
    }
}

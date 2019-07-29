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

import static ai.fetch.oef.InterfacesKt.cfpQueryFrom;
import static ai.fetch.oef.InterfacesKt.proposalsFrom;
import static ai.fetch.oef.SchemaKt.descriptionOf;
import static ai.fetch.oef.SchemaKt.descriptionPair;

public class WeatherClient extends OEFAgent {

    private static Logger log = LogManager.getLogger(WeatherStation.class.getName());
    private String publicKey;

    public WeatherClient(String publicKey, String oefAddress, int oefPort) {
        super(publicKey, oefAddress, oefPort);
        this.publicKey = publicKey;
    }

    @Override
    public void onAccept(int answerId, int dialogueId, @NotNull String origin, int target) {
    }

    @Override
    public void onCFP(int answerId, int dialogueId, @NotNull String origin, int target, @NotNull CFPQuery cfpQuery) {
    }

    @Override
    public void onDecline(int i, int i1, @NotNull String s, int i2) {

    }

    @Override
    public void onMessage(int i, int i1, @NotNull String s, @NotNull ByteBuffer content) {
        String data = WeatherContract.messageCoder.decode(content).toString();
        log.info(MessageFormat.format("Received message from $origin. Message: {0}", data));
    }

    @Override
    public void onPropose(int answerId, int dialogueId, @NotNull String origin, int target, @NotNull Proposals proposals) {
        log.info(MessageFormat.format("Received propose from agent {0}", origin));
        List<Description> values = ((Proposals.TDescriptions)proposals).getValue();
        for (Description value : values) {
            value.getAttributeValues().forEach((KeyValue it)->{
                log.info(MessageFormat.format("Proposal: {0} {0}", it.getName(), it.getValue()));
            });
        }
        log.info("Accepting proposal");
        Context context = getContext(answerId, dialogueId, origin);
        context.swap();
        sendAccept(answerId, dialogueId, origin, answerId+1, context);
    }

    @Override
    public void onDialogueError(int i, int i1) {

    }

    @Override
    public void onOEFError(int i, @NotNull OEFError oefError) {
        log.error(MessageFormat.format("OEFError: msg_id: {0}, error: {1}", i, oefError));
    }

    @Override
    public void onSearchResult(int i, @NotNull List<String> agents) {
        log.info(MessageFormat.format("Found agents: {0}, sending cfps...", agents));
        for(String agent : agents){
            Context context = new Context();
            context.forAgent(agent, publicKey, true);
            sendCFP(1, 0, agent, 0, cfpQueryFrom(new Query()), context);
        }

    }

    @Override
    public void onSearchResultWide(int i, @NotNull List<SearchResultItem> result) {
        log.info(MessageFormat.format("Got wide search result: {0}", result));
    }

    public static void main(String[] args) {
        WeatherClient agent = new WeatherClient("weather_client", "127.0.0.1", 10000);

        if (!agent.connect()){
            log.info("Not connected...");
            return;
        }

        log.info("Query...");

        Relation.EQ eq_true = new Relation.EQ(true);

        Query query = new Query(
                List.of(
                        new Constraint(WeatherContract.WeatherAttr.Temperature.getName(), eq_true),
                        new Constraint(WeatherContract.WeatherAttr.AirPressure.getName(), eq_true),
                        new Constraint(WeatherContract.WeatherAttr.Humidity.getName(),    eq_true)//,
                      //  new Constraint("location", new Distance(new Location(435.4, 425.3), 20000.0))
                ),
                WeatherContract.WeatherDataModel
        );

        agent.searchServices(0, query);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            agent.stop();
        }));

        try {
            Thread.sleep(5000);
        } catch (InterruptedException ex) {}
    }
}

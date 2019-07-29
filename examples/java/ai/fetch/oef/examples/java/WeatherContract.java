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

import ai.fetch.oef.AttributeSchema;
import ai.fetch.oef.DataModel;

import java.nio.charset.Charset;
import java.util.List;

public class WeatherContract {

    public static class WeatherAttr {
        public static AttributeSchema WindSpeed   = new AttributeSchema(
                "wind_speed",
                AttributeSchema.Type.BOOL,
                true,
                "Provides wind speed measurements."
        );
        public static AttributeSchema Temperature = new AttributeSchema(
                "temperature",
                AttributeSchema.Type.BOOL,
                true,
                "Provides temperature measurements."
        );
        public static AttributeSchema AirPressure = new AttributeSchema(
                "air_pressure",
                AttributeSchema.Type.BOOL,
                true,
                "Provides air pressure measurements."
        );
        public static AttributeSchema Humidity    = new AttributeSchema(
                "humidity",
                AttributeSchema.Type.BOOL,
                true,
                "Provides humidity measurements."
        );
    }

    public static DataModel WeatherDataModel = new DataModel(
            "weather_data",
            List.of(WeatherAttr.WindSpeed, WeatherAttr.Temperature, WeatherAttr.AirPressure, WeatherAttr.Humidity),
            "All possible weather data."
    );

    public static Charset messageCoder = Charset.forName("UTF-8");
}

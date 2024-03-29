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

import ai.fetch.oef.AttributeSchema
import ai.fetch.oef.DataModel
import java.nio.charset.Charset

object WeatherAttr {

    val WindSpeed   = AttributeSchema("wind_speed",   AttributeSchema.Type.BOOL, true, "Provides wind speed measurements.")
    val Temperature = AttributeSchema("temperature",  AttributeSchema.Type.BOOL, true, "Provides temperature measurements.")
    val AirPressure = AttributeSchema("air_pressure", AttributeSchema.Type.BOOL, true, "Provides air pressure measurements.")
    val Humidity    = AttributeSchema("humidity",     AttributeSchema.Type.BOOL, true, "Provides humidity measurements.")
}

val WeatherDataModel = DataModel(
    "weather_data",
    listOf(WeatherAttr.WindSpeed, WeatherAttr.Temperature, WeatherAttr.AirPressure, WeatherAttr.Humidity),
    "All possible weather data."
)

val messageCoder: Charset = Charset.forName("UTF-8")

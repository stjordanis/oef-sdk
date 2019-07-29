# Examples

### Run Python Weather station

Before using the python examples installation of the python SDK is required by doing one of the following:
* installing this repo locally as described in root README.md
* installing OEF SDK from pypi: `pip install oef`


There is two way one can run the python weather station.

1) Without bazel:

       cd python/weather
       python3 weather_station.py 

    To run the client, simply change the last command to `python3 weather_client.py`
    
2) With bazel (execute the following commands in the examples directory):

    * To run the weather station use: `bazel run python/weather:server`
    * Running the weather client: `bazel run python/weather:client`
      
    
### Run Kotlin Weather station

* Compile the example using `./gradlew shadowJar`
* Run the weather station with the following command:

      java -cp gradle-build/libs/example-0.5.1-all.jar ai.fetch.oef.examples.weatherstation.WeatherStationKt


* To run the client use:

      java -cp gradle-build/libs/example-0.5.1-all.jar ai.fetch.oef.examples.weatherstation.WeatherClientKt
  

### Run Java Weather station

* Compile the example using `./gradlew shadowJar`
* Run the weather station with the following command:

      java -cp gradle-build/libs/example-0.5.1-all.jar ai.fetch.oef.examples.java.WeatherStation


* To run the client use:

      java -cp gradle-build/libs/example-0.5.1-all.jar ai.fetch.oef.examples.java.WeatherClient
  

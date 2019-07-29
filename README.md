# OEF SDK Monorepo

This repo provides OEF SDK for the following languages
* Python
* Kotlin
* Java


This README is a high level introduction to agent development, see more details in the official [documentation](https://docs.fetch.ai/oef/).



### Using the Kotlin/Java SDK

   To develop Fetch agents in either Java or Kotlin, first we need to add [jitpack](https://jitpack.io/) to `repositories { ` in our 
   build.gradle file. After enabling jitpack we have to add oef-sdk dependency to our project (`dependencies {` in build.gradle):
   
    implementation "com.github.fetchai:oef-sdk:0.5.1"

To write agents we need to extend `OEFAgent` class from `ai.fetch.oef` package.


### Using the Python SDK

To use the python SDK we need to install it from pypi first using the following command (at least python 3.6 is required):

    pip install oef
    
 

### Building the SDK locally

#### Python
  To build the python SDK you have to use bazel. The following command will build the SDK and will install it in your local machine:
  ```
	bazel run //:pypi_local
  ```

### Kotlin/Java
  To build the SDK you have to use gradle. The following commands will build the SDK and install it into your local maven repository:
  ```
	./gradlew clean
	./gradlew build -x test
	./gradlew publishToMavenLocal
  ```
  After successfully installing the SDK locally, add `mavenLocal()` to `repositories { ` in your build.gradle file, which
  enables you to add oef-sdk as dependency(same as in jitpack version): `implementation "com.github.fetchai:oef-sdk:0.5.1"`
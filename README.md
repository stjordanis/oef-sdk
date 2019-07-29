# OEF SDK Monorepo

This repo provides OEF SDK for the following languages
* Python
* Kotlin
* Java

Building the SDK:

* Python
  To build the python SDK you have to use bazel. The follwoing command will build the SDK and will install it in your local machine:
  ```
	bazel run //:pypi_local
  ```

* Kotlin/Java
  To build the SDK you have to use gradle. The following commands will build the SDK and install it into your local maven repository:
  ```
	./gradlew clean
	./gradlew build -x test
	./gradlew publishToMavenLocal
  ```

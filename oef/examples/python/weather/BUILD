py_binary(
    name = "client",
    main = "weather_client.py",
    srcs = [
         "weather_client.py",
         "weather_schema.py"
    ],
    data = [
    ],
    deps = [
        "//oef/src/python:py_oef",
    ]
)

py_binary(
    name = "server",
    main = "weather_station.py",
    srcs = [
         "weather_station.py",
         "weather_schema.py"
    ],
    data = [
    ],
    deps = [
        "//oef/src/python:py_oef",
    ]
)

py_binary(
    name = "server_ssl",
    main = "weather_station_secure.py",
    srcs = [
         "weather_station_secure.py",
         "weather_schema.py"
    ],
    data = [
        "//oef/examples/resources:keys",
    ],
    deps = [
        "//oef/src/python:py_oef",
    ]
)
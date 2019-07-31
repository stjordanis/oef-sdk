py_library(
    name="oef_conn2",
    visibility = ["//visibility:public"],
    srcs = [ x for x in glob(["*.py"]) if x[0] in "ABCDEFGHIJKLMNOPQRSTUVWXYZ" ],
    deps=[
        "//protocol/src/proto:py_oef_protocol",
    ],
    data = [
    ]
)

py_binary(
    name="conn",
    visibility = ["//visibility:public"],
    srcs = ["asyncio-connecting.py"],
    main="asyncio-connecting.py",
    deps = [":oef_conn2"]
)
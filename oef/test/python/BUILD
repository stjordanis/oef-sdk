py_library(
    name = "classes",
    srcs = glob(["*Test.py"]),
    deps = [
        "//utils/src/python:py_utils",
        "//oef/src/python:py_oef",
        "//protocol/src/python:py_protocol_utils"
    ],
)

py_test(
    name = "tests",
    main = "tests.py",
    srcs = [
         "tests.py",
    ],
    data = [
    ],
    deps = [
        "//utils/src/python:py_utils",
        ":classes",
    ]
)
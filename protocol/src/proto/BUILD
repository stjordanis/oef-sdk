load("@com_google_protobuf//:protobuf.bzl", "py_proto_library")

proto_library(
    name = "oef_protocol",
    srcs = glob(["*.proto"]),
    licenses = [ "unencumbered" ],
    visibility = ["//visibility:public"],
)

py_proto_library(
    name = "py_oef_protocol",
    srcs = glob(["*.proto"]),
    protoc = "@com_google_protobuf//:protoc",
    default_runtime = "@com_google_protobuf//:protobuf_python",
    visibility = ["//visibility:public"],
    licenses = [ "unencumbered" ],
)

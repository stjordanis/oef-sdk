load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load("@bazel_tools//tools/build_defs/repo:git.bzl", "new_git_repository")
load("@bazel_tools//tools/build_defs/repo:git.bzl", "git_repository")

http_archive(
    name = "com_google_protobuf",
    sha256 = "1e622ce4b84b88b6d2cdf1db38d1a634fe2392d74f0b7b74ff98f3a51838ee53",
    strip_prefix = "protobuf-3.8.0",
    urls = ["https://github.com/protocolbuffers/protobuf/archive/v3.8.0.zip"],
)

load("@com_google_protobuf//:protobuf_deps.bzl", "protobuf_deps")

protobuf_deps()

http_archive(
    name = "six_archive",
    build_file = "@//:six.BUILD",
    sha256 = "d16a0141ec1a18405cd4ce8b4613101da75da0e9a7aec5bdd4fa804d0e0eba73",
    urls = ["https://files.pythonhosted.org/packages/dd/bf/4138e7bfb757de47d1f4b6994648ec67a51efe58fa907c1e11e350cddfca/six-1.12.0.tar.gz"],
)

bind(
  name = "six",
  actual = "@six_archive//:six",
)

http_archive(
    name = "bazel_skylib",
    sha256 = "bbccf674aa441c266df9894182d80de104cabd19be98be002f6d478aaa31574d",
    strip_prefix = "bazel-skylib-2169ae1c374aab4a09aa90e65efe1a3aad4e279b",
    urls = ["https://github.com/bazelbuild/bazel-skylib/archive/2169ae1c374aab4a09aa90e65efe1a3aad4e279b.tar.gz"],
)

http_archive(
    name = "pypi_six",
    url = "https://files.pythonhosted.org/packages/dd/bf/4138e7bfb757de47d1f4b6994648ec67a51efe58fa907c1e11e350cddfca/six-1.12.0.tar.gz",
    build_file_content = """
py_library(
    name = "six",
    srcs = ["six.py"],
    visibility = ["//visibility:public"],
)
    """,
    strip_prefix = "six-1.12.0",
)

http_archive(
    name = "protobuf_python",
    url = "https://files.pythonhosted.org/packages/65/95/8fe278158433a9bc34723f9ecbdee3097fb6baefaca932ec0331a9f80244/protobuf-3.8.0.tar.gz",
    build_file_content = """
py_library(
    name = "protobuf_python",
    srcs = glob(["google/protobuf/**/*.py"]),
    visibility = ["//visibility:public"],
    imports = [
        "@pypi_six//:six",
    ],
)
    """,
    strip_prefix = "protobuf-3.8.0",
)

new_git_repository(
    name = "googletest",
    build_file_content = """
cc_library(
    name = "gtest",
    srcs = [
         "googletest/src/gtest-all.cc",
         "googlemock/src/gmock-all.cc",
    ],
    hdrs = glob([
         "**/*.h",
         "googletest/src/*.cc",
         "googlemock/src/*.cc",
    ]),
    includes = [
        "googlemock",
        "googletest",
        "googletest/include",
        "googlemock/include",
    ],
    linkopts = ["-pthread"],
    visibility = ["//visibility:public"],
)

cc_library(
    name = "gtest_main",
    srcs = [
         "googlemock/src/gmock_main.cc"
    ],
    linkopts = ["-pthread"
    ],
    visibility = [
         "//visibility:public"
    ],
    deps = [
         ":gtest"
    ],
)

""",
    remote = "https://github.com/google/googletest",
    tag = "release-1.8.0",
)

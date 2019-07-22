##empty
#load("//:pypi_package.bzl", "pypi_package")
#
#
#
#pypi_package(
#    name = "py_oef_pkg",
#    version = "0.1",
#    description = "OEF Python SDK version 2",
#    long_description = "README.md",
#    classifiers = [
#        "Development Status :: 4 - Beta",
#        "Environment :: Console",
#        "Intended Audience :: Developers",
#        "License :: OSI Approved :: MIT License",
#        "Operating System :: POSIX",
#        "Programming Language :: Python :: 3.5",
#        "Programming Language :: Python :: 3.6",
#        "Programming Language :: Python :: 3.7",
#        "Topic :: Software Development :: Testing",
#        "Topic :: Software Development :: Libraries :: Python Modules"
#    ],
#    keywords = "",
#    url = "https://fetch.ai/",
#    author = "Fetch.AI",
#    author_email = "community@fetch.ai ",
#    license = "Apache 2.0",
#    packages = ["//oef/src/python:py_oef"],
#    #test_suite = "nose.collector",
#    #tests_require = ["nose"],
#    #pkg_path= "oef/src/python"
#)

load("//:pypi.bzl", "pypi_package", "pypi_install")

filegroup(
    name="python_sdk_readme",
    srcs=["PYREADME.md"],
    visibility = ["//visibility:public"],
)

pypi_package(
    name="pypi_release",
    deps=[
        "//oef/src/python:py_oef",
        "//protocol/src/proto:py_oef_protocol",
        "//protocol/src/python:py_protocol_utils",
        "//utils/src/python:py_utils"
    ],
    title = 'oef',
    description = 'SDK for OEF Agent development.',
    url = 'https://github.com/fetchai/oef-sdk-python.git',
    version = '0.5.2',
    build = '0x000510',
    author = 'Fetch.AI Limited',
    author_email = 'community@fetch.ai',
    license = 'Apache 2.0',
    copyright = '2019 Fetch.AI Limited',
    package='oef',
    classifiers=[
        'Development Status :: 3 - Alpha',
        'Intended Audience :: Developers',
        'Natural Language :: English',
        'License :: OSI Approved :: Apache Software License',
        'Programming Language :: Python :: 3.5',
        'Programming Language :: Python :: 3.6',
        'Programming Language :: Python :: 3.7',
    ],
    install_requires=["protobuf", "colorlog", "graphviz"],
    tests_require=["tox"],
    python_requires='>=3.5',
    long_description="//:python_sdk_readme"
)

pypi_install(
    name="pypi_local",
    deps=[
        "//oef/src/python:py_oef",
        "//protocol/src/proto:py_oef_protocol",
        "//protocol/src/python:py_protocol_utils",
        "//utils/src/python:py_utils"
    ],
    title = 'oef',
    description = 'SDK for OEF Agent development.',
    url = 'https://github.com/fetchai/oef-sdk-python.git',
    version = '0.5.2',
    build = '0x000510',
    author = 'Fetch.AI Limited',
    author_email = 'community@fetch.ai',
    license = 'Apache 2.0',
    copyright = '2019 Fetch.AI Limited',
    package='oef',
    classifiers=[
        'Development Status :: 3 - Alpha',
        'Intended Audience :: Developers',
        'Natural Language :: English',
        'License :: OSI Approved :: Apache Software License',
        'Programming Language :: Python :: 3.5',
        'Programming Language :: Python :: 3.6',
        'Programming Language :: Python :: 3.7',
    ],
    install_requires=["protobuf", "colorlog", "graphviz"],
    tests_require=["tox"],
    python_requires='>=3.5',
    long_description="//:python_sdk_readme"
)
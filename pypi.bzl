# -*- coding: utf-8 -*-

# ------------------------------------------------------------------------------
#
#   Copyright 2018 Fetch.AI Limited
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.
#
# ------------------------------------------------------------------------------

_FETCH_HEADER = """#!/usr/bin/env python3
# -*- coding: utf-8 -*-

# ------------------------------------------------------------------------------
#
#   Copyright 2018 Fetch.AI Limited
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.
#
# ------------------------------------------------------------------------------
"""

_INIT_FILE="""{}

\"""
Python SDK for OEF Agent development.
\"""
""".format(_FETCH_HEADER)


_VERSION_FILE_TEMPLATE="""{header}

__title__ = '{title}'
__description__ = '{description}'
__url__ = '{url}'
__version__ = '{version}'
__build__ = {build}
__author__ = '{author}'
__author_email__ = '{author_email}'
__license__ = '{license}'
__copyright__ = '{copyright}'
"""


_SETUP_FILE_TEMPLATE="""{header}
from setuptools import setup
import os

here = os.path.abspath(os.path.dirname(__file__))
about = {{}}
with open(os.path.join(here, '__version__.py'), 'r') as f:
    exec(f.read(), about)

with open(os.path.join(here, 'README.md'), 'r') as f:
    readme = f.read()


setup(
    name=about['__title__'],
    description=about['__description__'],
    version=about['__version__'],
    author=about['__author__'],
    author_email=about['__author_email__'],
    url=about['__url__'],
    long_description=readme,
    long_description_content_type='text/markdown',
    packages=[{packages}],
    cmdclass={{}},
    classifiers=[
        {classifiers}
    ],
    install_requires=[{install_requires}],
    tests_require=[{tests_require}],
    python_requires='{python_requires}',
    license=about['__license__'],
    zip_safe=False
)
"""


def _add_file(ctx, files, name, content):
    f = ctx.actions.declare_file(name)
    ctx.actions.write(f, content=content)
    files.append(f)

def _quote(x):
    return  "\"{}\"".format(x)

def map(l, func):
    return [func(e) for e in l]

def _build_pypi_package(ctx):
    root_pkg_dir = "pypi_pkg/"
    pub_dir = ctx.attr.package+"/"
    pkg_dir = root_pkg_dir+pub_dir
    files = []
    for dep in ctx.attr.deps:
        for file in dep.files.to_list():
            f = ctx.actions.declare_file(pkg_dir+file.basename)
            ctx.actions.run_shell(
                command="sed -E 's/^from.*\.src\.(proto|python)/from \./' {} | sed -E 's/\.\./\./g'> {}".format(file.path, f.path),
                outputs=[f],
                inputs=[file]
            )
            files.append(f)
    f = ctx.actions.declare_file(pkg_dir+"README.md")
    long_description=ctx.attr.long_description.files.to_list()[0]
    ctx.actions.run_shell(
        command="cp {} {}".format(long_description.path, f.path),
        outputs=[f],
        inputs=[long_description]
    )
    files.append(f)
    _add_file(ctx, files, pkg_dir+"__init__.py", content=_INIT_FILE)
    _add_file(ctx, files, pkg_dir+"__version__.py", content=_VERSION_FILE_TEMPLATE.format(
        header=_FETCH_HEADER,
        title=ctx.attr.title,
        description=ctx.attr.description,
        url=ctx.attr.url,
        version=ctx.attr.version,
        build=ctx.attr.build,
        author=ctx.attr.author,
        author_email=ctx.attr.author_email,
        license=ctx.attr.license,
        copyright=ctx.attr.copyright
    ))
    _add_file(ctx, files, pkg_dir+"setup.py", content=_SETUP_FILE_TEMPLATE.format(
        header=_FETCH_HEADER,
        packages=_quote(ctx.attr.package),
        classifiers=", ".join(map(ctx.attr.classifiers, _quote)),
        install_requires=", ".join(map(ctx.attr.install_requires, _quote)),
        tests_require=", ".join(map(ctx.attr.tests_require, _quote)),
        python_requires=ctx.attr.python_requires,
    ))

    exec_file=ctx.actions.declare_file("main_executable")
    ctx.actions.write(exec_file, content="""
    #/bin/bash

    cd {root_pkg_dir}

    for key in "$@"
    do
      case $key in
        --upload=*)
          upload="${{key#*=}}"
          shift
          ;;
      esac
    done

    python3 {pub_dir}setup.py sdist
    python3 {pub_dir}setup.py bdist_wheel

    if [ "$upload" = "test" ]
    then
      pypi_repo="https://test.pypi.org/legacy/"
    fi

    if [ -n "$upload" ]
    then
        if [ "$upload" = "test" ]
        then
            echo "Publishing package to the test pypi repo ($pypi_repo)!"
            python3 -m twine upload --repository-url "$pypi_repo" dist/*
        elif [ "$upload" = "release" ]
        then
            echo "Publishing package to the main pypi repo!"
            python3 -m twine upload dist/*
        fi
    fi

    """.format(
        pub_dir=pub_dir,
        root_pkg_dir=root_pkg_dir
    ))
    return [DefaultInfo(
        files=depset(files),
        executable=exec_file,
        runfiles=ctx.runfiles(files=files)
    )]

pypi_package = rule(
    implementation=_build_pypi_package,
    attrs={
        "deps": attr.label_list(),
        "title": attr.string(),
        "description": attr.string(),
        "url": attr.string(),
        "version": attr.string(),
        "build": attr.string(),
        "author": attr.string(),
        "author_email": attr.string(),
        "license": attr.string(),
        "copyright": attr.string(),
        "package": attr.string(),
        "classifiers": attr.string_list(),
        "install_requires": attr.string_list(),
        "tests_require": attr.string_list(),
        "python_requires": attr.string(),
        "long_description": attr.label()

    },
    executable=True,
)

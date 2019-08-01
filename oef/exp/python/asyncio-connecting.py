#!/usr/bin/env python3

import asyncio
import threading
import sys
import functools
import time

import AsyncioCore
import Connection

def on_connect_ok(conn=None, url=None):
    print("URL OK:", url)

def on_connect_fail(conn=None, url=None, ex=None):
    print("URL FAIL:", url, ex)

def main():
    core = AsyncioCore.AsyncioCore()
    conn = Connection.Connection(core)

    core.run_threaded()

    def thing(foo):
        print("thing:", foo)

    c = 0
    while c < 5:
        time.sleep(1)
        if c == 0:
            conn.connect("127.0.0.1:10000", success=on_connect_ok, failure=on_connect_fail)
        #core.call_soon(thing, c)
        c += 1

    print("Scheduled shutdown.")

    #conn.stop()
    core.stop()

if __name__ == "__main__":
    main()

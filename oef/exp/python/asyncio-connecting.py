#!/usr/bin/env python3

import asyncio
import threading
import sys
import functools
import time

import AsyncioCore
import Connection
import OefMessageHandler

gMessages = OefMessageHandler.OefMessageHandler(target=None)

def on_connect_ok(conn=None, url=None, conn_name=None, **kwargs):
    conn.set_message_handler(gMessages)
    print("URL OK:", url, conn_name)

def on_connect_fail(conn=None, url=None, ex=None, **kwargs):
    print("URL FAIL:", url, ex)

def main():
    core = AsyncioCore.AsyncioCore()
    conn1 = Connection.Connection(core)
    conn2 = Connection.Connection(core)
    core.run_threaded()

    c = 0
    while c < 5:
        time.sleep(1)
        if c == 0:
            conn1.connect("127.0.0.1:10000", success=on_connect_ok, failure=on_connect_fail, public_key="moocows")
            conn2.connect("127.0.0.1:10001", success=on_connect_ok, failure=on_connect_fail, public_key="moopoo")
        c += 1

    print("Scheduled shutdown.")
    core.stop()

if __name__ == "__main__":
    main()

#!/usr/bin/env python3

import asyncio
import threading
import sys
import functools
import time

import AsyncioCore
import Connection
import OefMessageHandler


def logger(*args):
    print(">>", *args)

def on_connect_ok(conn=None, url=None, conn_name=None, **kwargs):
    conn.set_message_handler(gMessages)
    logger("on_connect_ok[{}] ".format(id(conn)), url)

def on_connect_fail(conn=None, url=None, ex=None, **kwargs):
    logger("on_connect_fail[{}] ".format(id(conn)), url, " => ", ex)

gMessages = OefMessageHandler.OefMessageHandler(target=None, logger=logger)

def main():
    core = AsyncioCore.AsyncioCore(logger=logger)
    conn1 = Connection.Connection(core, logger=logger)
    conn2 = Connection.Connection(core, logger=logger)
    core.run_threaded()

    c = 0
    while c < 5:
        time.sleep(1)
        if c == 0:
            conn1.connect(url="127.0.0.1:10000", success=on_connect_ok, failure=on_connect_fail, public_key="moocows")
            conn2.connect(url="127.0.0.1:10001", success=on_connect_ok, failure=on_connect_fail, public_key="moopoo")
        c += 1

    print("Scheduled shutdown.")
    core.stop()

if __name__ == "__main__":
    main()

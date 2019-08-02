import asyncio
import threading
import sys
import functools
import time

class AsyncioCore(object):

# If you want to use this in an ASYNCIO world, pass a suitable loop into the constructor
    def __init__(self, loop = None, logger=None):
        self.loop = loop
        self.logger = logger or print
        self.done = None
        if loop:
            self.done = loop.create_future()

        self.connections = set()
        self.url_to_connections = {}

# This is the interface for use in THREADED applications. Call
# run_threaded() to start the app's networking and then stop() when
# you're done.

    def run_threaded(self):
        def execute(core):
            core.loop = asyncio.new_event_loop()
            core.loop.run_forever()

        self.thread = threading.Thread(target=execute, args=(self,) )
        self.thread.start()

    def stop(self):
        conns = self.connections
        self.connections = set()
        for conn in conns:
            conn.close()
        for attempt in range(0,10):
            if len(asyncio.all_tasks(self.loop)) == 0:
                break
            time.sleep(0.3)
        if self.done:
            self.done.set_result(True)
            self.done = None
        elif self.loop:
            self.loop.call_soon_threadsafe(self.loop.stop)
            self.thread.join()
            self.thread = None
            self.loop = None

# If you want to use this in an ASYNCIO world, pass a suitable loop
# into the constructor and this function will give you a waitable
# which will complete when stop() has been called.

    def get_awaitable(self):
        return asyncio.gather(self.done)

# functions for scheduling work onto our loop. Note that the loop runs
# the networking and hence long-running tasks posted to it will block
# the network from running until they complete.

    def call_soon_async(self, func, *args, **kwargs):
        def taskify(func, args, kwargs):
            asyncio.create_task(func(*args, **kwargs))
        return self.loop.call_soon_threadsafe(taskify,func, args, kwargs)

    def call_soon(self, func, *args):
        if self.loop:
            return self.loop.call_soon_threadsafe(func, *args)
        else:
            raise ValueError("Start my loop first!")

    def call_later(self, seconds, func, *args):
        if self.loop:
            return self.loop.call_later(seconds, func, *args)
        else:
            raise ValueError("Start my loop first!")

    def call_soon(self, func, *args):
        if self.loop:
            return self.loop.call_soon_threadsafe(func, *args)
        else:
            raise ValueError("Start my loop first!")

# functions used by Connection objects.

    def register_connection(self, connection):
        self.connections.add(connection)

    def deregister_connection(self, connection):
        self.connections.discard(connection)

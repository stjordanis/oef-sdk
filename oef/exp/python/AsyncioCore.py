import asyncio
import threading
import sys
import functools
import time

class AsyncioCore(object):
    def __init__(self, loop = None):
        self.loop = loop
        self.done = None
        if loop:
            self.done = loop.create_future()

    def call_soon_async(self, func, *args):
        #print("call_soon_async: on ", threading.get_ident())
        def taskify(func, args):
            #print("call_soon_async.taskify: on ", threading.get_ident())
            asyncio.create_task(func(*args))
        self.loop.call_soon_threadsafe(taskify,func, args)

    def call_soon(self, func, *args):
        if self.loop:
            self.loop.call_soon_threadsafe(func, *args)
        else:
            raise ValueError("Start my loop first!")

    def call_later(self, seconds, func, *args):
        if self.loop:
            self.loop.call_later(seconds, func, *args)
        else:
            raise ValueError("Start my loop first!")

    def call_soon(self, func, *args):
        if self.loop:
            self.loop.call_soon_threadsafe(func, *args)
        else:
            raise ValueError("Start my loop first!")

    def run_threaded(self):
        #print("run_threaded:new loop")

        def execute(core):
            #print(">run_threaded::execute")
            print("run_threaded.execute: on ", threading.get_ident())
            core.loop = asyncio.new_event_loop()
            core.loop.run_forever()
            #print("<run_threaded::execute")

        #print("run_threaded:thr create")
        self.thread = threading.Thread(target=execute, args=(self,) )
        #print("run_threaded:thr start..")
        self.thread.start()
        #print("run_threaded:thr started")

    def get_awaitable(self):
        return asyncio.gather(self.done)

    def stop(self):
        if self.done:
            self.done.set_result(True)
            self.done = None
        elif self.loop:
            self.loop.stop()
            self.thread.join()
            self.thread = None
            self.loop = None

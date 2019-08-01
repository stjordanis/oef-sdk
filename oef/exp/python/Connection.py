import asyncio
import struct
import queue

import OefLoginHandler

# This class is annoyingly complicated, but I'll explain.
#
# The "do_X" functions do the work asked for by the X
# functions. Calling them must happen from INSIDE the core's event
# loop. So a task to call them 'soon' is posted to the loop.
#
# Reading pulls LENGTH-prefixed protobufs off the comms reader and
# sends them to the message-handler's 

class Connection(object):
    def __init__(self, core):
        self.reader = None
        self.writer = None
        self.core = core
        self.core.connections.add(self)
        self.url = None
        self.message_handler = None
        self.send_loop = None
        self.recv_loop = None

    def new_message_handler_type(self, message_handler_type):
        self.message_handler = message_handler_type(self)

    def connect(self, url, success=None, failure=None, **kwargs):
        if url == self.url:
            return
        data = {}
        data.update(kwargs)
        data .update({
            "url": url,
            "success": success,
            "failure": failure,
        })
        self.core.call_soon_async(self.do_connect, data)

    def send(self, data):
        self.core.call_soon_async(self.do_send, data)

    async def do_send(self, data):
        await self.outq.put(data)

    def close(self):
        self.core.connections.discard(self)
        self.core.call_soon_async(self.do_stop)

    async def do_stop(self):
        self.reader = None
        w = self.writer
        self.writer = None
        if w:
            w.close();
            await w.wait_closed()
        await self.outq.put(None)
        if self.send_loop:
            self.send_loop.cancel()
        if self.recv_loop:
            self.recv_loop.cancel()

    async def do_connect(self, data):
        if self.reader:
            self.reader = None
        if self.writer:
            self.writer.close()
            self.writer= None

        try:
            self.url = data['url']
            self.addr, _, self.port = self.url.partition(':')
            self.port = int(self.port)
            x = await asyncio.open_connection(self.addr, self.port)
            self.outq = asyncio.Queue(maxsize=0)
            self.reader, self.writer = x
            try:
                self.send_loop = self.core.call_soon_async(self.do_send_loop)
                self.recv_loop = self.core.call_soon_async(self.do_recv_loop)
                self.message_handler = OefLoginHandler.OefLoginHandler(self, data)
            except Exception as ex:
                print(ex)
        except Exception as ex:
            self.message_handler.handle_failure(ex)

    async def do_send_loop(self):
        sendable = await self.outq.get()
        if not self.writer or not sendable:
            #print("do_send_loop found null writer")
            return
        self.outq.task_done()
        await self._transmit(sendable)
        if not self.writer or not sendable:
            #print("do_send_loop found null writer")
            return
        self.core.call_soon_async(self.do_send_loop)

    def _message_arrived(self, data):
       self.message_handler.incoming(data)

    async def do_recv_loop(self):
        data = None
        try:
            data = await self._receive()
            if data == None:
                #print("do_recv_loop got EOF")
                return
            self._message_arrived(data)
            self.core.call_soon_async(self.do_recv_loop)
        except EOFError:
            #print("do_recv_loop got EOF")
            pass

    async def _transmit(self, body):
        nbytes = len(body)
        header = struct.pack("I", nbytes)
        msg = header + body
        self.writer.write(msg)
        await self.writer.drain()

    async def _receive(self):
        nbytes_packed = await self.reader.read(len(struct.pack("I", 0)))
        if len(nbytes_packed) == 0:
            raise EOFError()
        nbytes = struct.unpack("I", nbytes_packed)[0]
        data = b""
        while self.reader and len(data) < nbytes:
            input_bytes = await self.reader.read(nbytes - len(data))
            if len(input_bytes) == 0:
                raise EOFError()
            data += input_bytes
        return data

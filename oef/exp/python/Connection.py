import asyncio
import struct
import queue

import OefLoginHandler

class Connection(object):
    def __init__(self, core):
        self.reader = None
        self.writer = None
        self.core = core
        self.url = None
        self.message_handler = None

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

    async def do_connect(self, data):
        if self.reader:
            self.reader.close()
            self.reader = None
        if self.writer:
            self.writer.close()
            self.writer= None

        on_fail = data.get('failure', None)
        on_succ = data.get('success', None)
        try:
            self.url = data['url']
            self.addr, _, self.port = self.url.partition(':')
            self.port = int(self.port)
            x = await asyncio.open_connection(self.addr, self.port)
            self.outq = asyncio.Queue(maxsize=0)
            self.reader, self.writer = x
            try:
                self.core.call_soon_async(self.do_send_loop)
                self.core.call_soon_async(self.do_recv_loop)
                self.message_handler = OefLoginHandler.OefLoginHandler(self)
            except Exception as ex:
                print(ex)
        except Exception as ex:
            h=False
            if on_fail:
                try:
                    on_fail(self, self.url, ex)
                    h=True
                except:
                    pass
            if not h:
                print("Connection.do_connect:failure - ", ex)

    async def do_send_loop(self):
        while self.writer and not self.writer.is_closing():
            sendable = await self.outq.get()
            await self._transmit(sendable)

    def _message_arrived(self, data):
       self.message_handler.incoming(data)

    async def do_recv_loop(self):
        while self.reader and not self.reader.at_eof():
            data = None
            try:
                data = await self._receive()
                self._message_arrived(data)
            except EOFError:
                print("EOF DETECTED")
                if self.reader:
                    self.reader.close()
                    self.reader = None
                if self.writer:
                    self.writer.close()
                    self.writer= None

    async def _transmit(self, body):
        nbytes = len(body)
        header = struct.pack("I", nbytes)
        msg = header + body
        self.writer.write(msg)
        await self.writer.drain()

    async def _receive(self):
        """
        Receive a Protobuf message.
        :return: ``None``
        :raises OEFConnectionError: if the connection has not been established yet.
        """
        nbytes_packed = await self.reader.read(len(struct.pack("I", 0)))
        if len(nbytes_packed) == 0:
            raise EOFError()
        nbytes = struct.unpack("I", nbytes_packed)[0]
        data = b""
        while len(data) < nbytes:
            input_bytes = await self.reader.read(nbytes - len(data))
            if len(input_bytes) == 0:
                raise EOFError()
            data += input_bytes
        return data

    

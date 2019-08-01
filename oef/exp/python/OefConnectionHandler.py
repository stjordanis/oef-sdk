from protocol.src.proto import agent_pb2

import OefProtoBase

class OefConnectionHandler(OefProtoBase.OefProtoBase):
    def __init__(self, data, **kwargs):
        self.success = data.get('success', lambda x,y: None)
        self.failure = data.get('failure', lambda x,y: None)
        self.conn = data.get('conn', None)

    def output(self, data, target):
        target . send(data)

    def incoming(self, data, connection_name=None, conn=None):
        raise ValueError("Messages arrived before connection was complete.")

    def handle_failure(self, exception, conn):
        self.failure(conn=conn, url=conn.url, ex=exception, conn_name=conn.name)
        conn.close()

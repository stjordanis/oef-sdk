from protocol.src.proto import agent_pb2

import OefProtoBase
import OefMessageHandler

class OefLoginHandler(OefProtoBase.OefProtoBase):
    def __init__(self, conn=None, public_key=None, success=None, failure=None, logger=None, **kwargs):
        super().__init__(**kwargs)
        self.success = success
        self.failure = failure
        self.logger = logger

        if not self.failure:
            self.failure = lambda x,y: None
        if not self.success:
            self.success = lambda x,y: None
        self.public_key = public_key or None
        self.output(
            self.make_message(
                agent_pb2.Agent.Server.ID,
                {
                    "public_key": self.public_key,
                }
            ),
            conn
        )

    def output(self, data, conn):
        conn . send(data)

    def incoming(self, data, connection_name, conn):
        inp_chall = self.decode_message(agent_pb2.Server.Phrase, data)
        inp_conn = self.decode_message(agent_pb2.Server.Connected, data)

        if 'phrase' in inp_chall:
            self.output(
                self.make_message(
                    agent_pb2.Agent.Server.Answer,
                    {
                        "answer": inp_chall['phrase'][::-1],
                        "capability_bits": {
                            "will_heartbeat": True,
                        },
                    }
                    ),
                conn
                )
            return

        if 'failure' in inp_chall:
            self.handle_failure(ValueError("rejected before challenge"), conn)
            return

        if 'status' in inp_conn:
            if inp_conn['status']:
                conn.new_message_handler_type(OefMessageHandler.OefMessageHandler, logger=self.logger)
                self.success(conn=conn, url=conn.url, conn_name=conn.name)
                return
            else:
                self.handle_failure(ValueError("bad challenge/response"), conn)
                return
        self.handle_failure(ValueError("bad login message from server"), conn)

    def handle_failure(self, exception, conn):
        self.failure(conn=conn, url=conn.url, ex=exception, conn_name=conn.name)
        conn.close()

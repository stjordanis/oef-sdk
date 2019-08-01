from protocol.src.proto import agent_pb2

import OefProtoBase
import OefMessageHandler

class OefLoginHandler(OefProtoBase.OefProtoBase):
    def __init__(self, target, data):
        self.target = target
        self.success = data.get('success', lambda x,y: None)
        self.failure = data.get('failure', lambda x,y: None)
        self.output(
            self.make_message(
                agent_pb2.Agent.Server.ID,
                {
                    "public_key": "cowpoos",
                }
            )
        )

    def output(self, data):
        self. target . send(data)

    def incoming(self, data):
        inp_chall = self.decode_message(agent_pb2.Server.Phrase, data)
        inp_conn = self.decode_message(agent_pb2.Server.Connected, data)

        if 'phrase' in inp_chall:
            self.output(
                self.make_message(
                    agent_pb2.Agent.Server.Answer,
                    {
                        "answer": inp_chall['phrase'][::-1][3],
                        "capability_bits": {
                            "will_heartbeat": True,
                        },
                    }
                    )
                )
            return

        if 'failure' in inp_chall:
            self.handle_failure(ValueError("rejected before challenge"))
            return

        if 'status' in inp_conn:
            if inp_conn['status']:
                self.success(self.target, self.target.url)
                self.target.new_message_handler_type(OefMessageHandler.OefMessageHandler)
                return
            else:
                self.handle_failure(ValueError("bad challenge/response"))
                return
        self.handle_failure(ValueError("bad login message from server"))

    def handle_failure(self, exception):
        self.failure(self.target, self.target.url, exception)
        self.target.close()

from protocol.src.proto import agent_pb2

import OefProtoBase
import OefMessageHandler

class OefLoginHandler(OefProtoBase.OefProtoBase):
    def __init__(self, target):
        self.target = target
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
                        "answer": inp_chall['phrase'][::-1],
                        "capability_bits": {
                            "will_heartbeat": True,
                        },
                    }
                    )
                )
            return

        if 'failure' in inp_chall:
            print("Fuck")
            exit(77)

        if 'status' in inp_conn:
            print("RAH!")
            self.target.new_message_handler_type(OefMessageHandler.OefMessageHandler)

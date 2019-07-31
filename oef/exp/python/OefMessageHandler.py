from protocol.src.proto import agent_pb2

import OefProtoBase

class OefMessageHandler(OefProtoBase.OefProtoBase):
    def __init__(self, target):
        self.target = target

    def output(self, data):
        self. target . send(data)

    def incoming(self, data):
        am = self.decode_message(agent_pb2.Server.AgentMessage, data)

        if 'ping' in am:
            self.output(self.make_message(agent_pb2.Envelope, {
                "msg_id": 0,
                "pong": {
                    "dummy": 77,
                }
            }))
            return

        print(am)

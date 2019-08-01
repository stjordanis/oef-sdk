from protocol.src.proto import agent_pb2

import OefProtoBase

class OefMessageHandler(OefProtoBase.OefProtoBase):
    def __init__(self, **kwargs):
        pass

    def output(self, data, target):
        target . send(data)

    def incoming(self, data, connection_name=None, conn=None):
        am = self.decode_message(agent_pb2.Server.AgentMessage, data)

        if 'ping' in am:
            self.output(
                self.make_message(
                    agent_pb2.Envelope,
                    {
                        "msg_id": 0,
                        "pong": {
                            "dummy": 77,
                        }
                    }
                ),
                conn
            )
            return True

        print(id(self), connection_name, am)
        return False

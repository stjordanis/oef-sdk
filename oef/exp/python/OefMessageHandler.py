from protocol.src.proto import agent_pb2

import OefProtoBase

class OefMessageHandler(OefProtoBase.OefProtoBase):
    def __init__(self, logger=None, **kwargs):
        self.logger = logger or print

    def output(self, data, target):
        target . send(data)

    def incoming(self, data, connection_name=None, conn=None):
        am = self.decode_message(agent_pb2.Server.AgentMessage, data)
        return self.incomingAgentMessage(am, connection_name=connection_name, conn=conn)

    def incomingAgentMessage(self, agentMessage, connection_name=None, conn=None):
        if 'ping' in agentMessage:
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
            self.logger("OefMessageHandler[{}].incoming:handled:".format(id(self)), connection_name, agentMessage)
            return True

        return False

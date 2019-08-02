import OefMessageHandler

class OefMultipleConnectionMessageHandler(OefMessageHandler.OefMessageHandler):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.conns = set()
        self.url_to_conn = {}

    def output(self, data, conn=None, url=None):
        conn = conn or url_to_conn.get(url, None)
        if conn:
            conn . send(data)
            return
        raise ValueError('conn is null, url {} is not found'.format(url))

    def incomingAgentMessage(self, agentMessage, connection_name=None, conn=None):
        r = False
        if conn:
            if conn not in self.conns:
                self.conns.add(conn)
                self.url_to_conn[conn.url] = conn
            if self.url_to_conn.get(conn.url, None) != conn:
                self.url_to_conn = {
                    k:v
                    for k,v
                    in self.url_to_conn.items()
                    if v != conn
                }
                self.url_to_conn[conn.url] = conn
        if super().incomingAgentMessage(agentMessage, connection_name=connection_name, conn=conn):
            return True

        self.logger("OefMultipleConnectionMessageHandler[{}].incoming:unknown:".format(id(self)), connection_name, agentMessage)
        return False

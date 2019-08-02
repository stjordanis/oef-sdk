
class OefMultipleConnectionMessageHandler(OefProtoBase.OefProtoBase):
    def __init__(self, **kwargs):
        conns = set()
        url_to_conn = {}

    def output(self, data, conn=None, url=None):
        conn = conn or url_to_conn.get(url, None)
        if conn:
            conn . send(data)
            return
        raise ValueError('conn is null, url {} is not found'.format(url))

    def incomingAgentMessage(self, agentMessage, connection_name=None, conn=None):
        if conn:
            if conn not in conns:
                conns.add(conn)
                url_to_conn[conn.url] = conn
            if url_to_conn.get(conn.url, None) != conn:
                url_to_conn = {
                    k:v
                    for k,v
                    in url_to_conn.items()
                    if v != conn
                }
                url_to_conn[conn.url] = conn
        if super().incomingAgentMessage(self, agentMessage, connection_name=connection_name, conn=conn):
            return True

        self.logger("OefMultipleConnectionMessageHandler[{}].incoming:unknown:".format(id(self)), connection_name, agentMessage)

from google.protobuf import json_format

import json
import struct
from abc import ABC, abstractmethod

class OefProtoBase(object):
    def __init__(self):
        pass

    def make_message(self, x, contents):
        json_string = json.dumps(contents)
        outbound = x()
        json_format.Parse(json_string, outbound, ignore_unknown_fields=True)
        bytes = outbound.SerializeToString()
        return bytes

    def decode_message(self, x, bytes):
        inbound = x()
        inbound.ParseFromString(bytes)
        json_string = json_format.MessageToJson(inbound, including_default_value_fields=True)
        contents = json.loads(json_string)
        return contents

    @abstractmethod
    def output(self, data, target):
        pass

    @abstractmethod
    def incoming(self, data, connection_name, conn):
        pass

    @abstractmethod
    def handle_failure(self, exception, conn):
        pass

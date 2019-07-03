from abc import ABC, abstractmethod


class ProtobufSerializable(ABC):
    """
    Interface that includes method for packing/unpacking to/from Protobuf objects.
    """

    @abstractmethod
    def to_pb(self):
        """Convert the object into a Protobuf object"""

    @classmethod
    @abstractmethod
    def from_pb(cls, obj):
        """
        Unpack a Protobuf object.

        :param obj: the Protobuf object to parse.
        :return: an instance of the class that implements the interface.
        """
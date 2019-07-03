from utils.src.python.helpers import haversine
from protocol.src.proto import dap_interface_pb2
from utils.src.python.Logging import has_logger
from protocol.src.python.Interfaces import ProtobufSerializable


class Location(ProtobufSerializable):
    """Data structure to represent locations (i.e. a pair of latitude and longitude)."""

    @has_logger
    def __init__(self, latitude: float, longitude: float):
        """
        Initialize a location.

        :param latitude: the latitude of the location.
        :param longitude: the longitude of the location.
        """
        self.latitude = latitude
        self.longitude = longitude

    @classmethod
    def from_pb(cls, obj: dap_interface_pb2.ValueMessage.Location):
        """
        From the ``Location`` Protobuf object to the associated instance of :class:`~oef.query.Location`.

        :param obj: the Protobuf object that represents the ``Location`` constraint.
        :return: an instance of :class:`~oef.query.Location` equivalent to the Protobuf object.
        """
        if obj.coordinate_system == "latlon":
            latitude = obj.v[0]
            longitude = obj.v[1]
            return cls(latitude, longitude)
        else:
            el = cls(0, 0)
            el.warning("Location coordinate system not supported: ", obj.coordinate_system)
            return el

    def to_pb(self) -> dap_interface_pb2.ValueMessage.Location:
        """
        From an instance of :class:`~oef.schema.Location` to its associated Protobuf object.

        :return: the Location Protobuf object that contains the :class:`~oef.schema.Location` constraint.
        """
        location_pb = dap_interface_pb2.ValueMessage.Location()
        location_pb.coordinate_system = "latlon"
        location_pb.unit = "deg"
        location_pb.v.append(self.latitude)
        location_pb.v.append(self.longitude)
        return location_pb

    def distance(self, other) -> float:
        return haversine(self.latitude, self.longitude, other.latitude, other.longitude)

    def __eq__(self, other):
        if type(other) != Location:
            return False
        else:
            return self.latitude == other.latitude and self.longitude == other.longitude


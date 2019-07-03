from abc import ABC, abstractmethod
from typing import Union, Type
from protocol.src.python import ProtoHelpers

from oef.src.python import QueryBuildingBlocks

ATTRIBUTE_TYPES = Union[float, str, bool, int]
ORDERED_TYPES = Union[int, str, float]


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


class ConstraintExpr(ProtobufSerializable, ABC):
    """
    This class is used to represent a constraint expression.
    """
    pass


class ConstraintType(ProtobufSerializable, ABC):
    """
    This class is used to represent a constraint type.
    """
    pass


class Relation(ConstraintType, ABC):
    """
    A constraint type that allows you to impose specific values
    for the attributes.

    The specific operator of the relation is defined in the
    subclasses that extend this class.
    """

    def __init__(self, value: ATTRIBUTE_TYPES) -> None:
        """
        Initialize a Relation object.

        :param value: the right value of the relation.
        """
        self.value = value

    @property
    @abstractmethod
    def _operator(self) -> str:
        """The operator of the relation."""

    def _get_type(self) -> Type[ATTRIBUTE_TYPES]:
        return type(self.value)


class OrderingRelation(Relation, ABC):
    """A specialization of the :class:`~oef.query.Relation` class to represent ordering relation (e.g. greater-than)."""

    def __init__(self, value: ORDERED_TYPES):
        super().__init__(value)

    def _get_type(self) -> str:
        return ProtoHelpers.pythonTypeToString(self.value)


class Eq(Relation):
    """
    The equality relation. That is, if the value of an attribute is equal to the value specified then
    the :class:`~oef.query.Constraint` with this constraint type is satisfied.

    Examples:
        All the books whose author is Stephen King

        >>> c = Constraint("author",  Eq("Stephen King"))
        >>> c.check(Description({"author": "Stephen King"}))
        True
        >>> c.check(Description({"author": "George Orwell"}))
        False

    """

    def _operator(self):
        return ProtoHelpers.OPERATOR_EQ


class Constraint(ConstraintExpr):
    """
    A class that represent a constraint over an attribute.
    """

    def __init__(self,
                 attribute_name: str,
                 constraint: Relation) -> None: # not Relation, ConstraintType
        self.attribute_name = attribute_name
        self.constraint = constraint
        self.leaf = QueryBuildingBlocks.Leaf(
            operator=constraint._operator,
            query_field_value=constraint.value,
            query_field_type=constraint._get_type(),
            target_field_name=attribute_name,
        )


class Query(ProtobufSerializable):
    """
    Representation of a search that is to be performed. Currently a search is represented as a
    set of key value pairs that must be contained in the description of the service/ agent.

    Examples:
        Return all the books written by Stephen King published after 1990, and available as an e-book:

        >>> attr_author   = AttributeSchema("author" ,         str,   True,  "The author of the book.")
        >>> attr_year     = AttributeSchema("year",            int,   True,  "The year of publication of the book.")
        >>> attr_ebook    = AttributeSchema("ebook_available", bool,  False, "If the book can be sold as an e-book.")
        >>> q = Query([
        ...     Constraint("author", Eq("Stephen King")),
        ...     Constraint("year", Gt(1990)),
        ...     Constraint("ebook_available", Eq(True))
        ... ])

        With a query, you can check that a `~oef.schema.Description` object satisfies the constraints.

        >>> q.check(Description({"author": "Stephen King", "year": 1991, "ebook_available": True}))
        True
        >>> q.check(Description({"author": "George Orwell", "year": 1948, "ebook_available": False}))
        False

    """

    def __init__(self,
                 constraints: List[ConstraintExpr],
                 model: Optional[DataModel] = None) -> None:
        """
        Initialize a query.

        :param constraints: a list of ``Constraint``.
        :param model: the data model where the query is defined.
        """
        self.constraints = constraints
        self.model = model

        self._check_validity()


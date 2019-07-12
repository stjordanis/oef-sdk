from abc import ABC, abstractmethod
from typing import Union, Tuple, List, Optional, Type
from protocol.src.python import ProtoHelpers

from oef.src.python import QueryBuildingBlocks
from oef.src.python.schema import AttributeSchema, Description, DataModel
from protocol.src.python.Interfaces import ProtobufSerializable
from protocol.src.python.Wrappers import Location
from protocol.src.proto import dap_interface_pb2

ATTRIBUTE_TYPES = Union[float, str, bool, int]
ORDERED_TYPES = Union[int, str, float]
RANGE_TYPES = Union[Tuple[str, str], Tuple[int, int], Tuple[float, float], Tuple[Location, Location]]
SET_TYPES = Union[List[float], List[str], List[bool], List[int], List[Location]]


class ConstraintExpr(ProtobufSerializable, ABC):
    """
    This class is used to represent a constraint expression.
    """

    @abstractmethod
    def check(self, description: Description) -> bool:
        """
        Check if a description satisfies the constraint expression.

        :param description: the description to check.
        :return: ``True`` if the description satisfy the constraint expression, ``False`` otherwise.
        """

    @abstractmethod
    def is_valid(self, data_model: DataModel) -> bool:
        """
        Check whether a constraint expression is valid wrt a data model. Specifically, check the following conditions:

        - If all the attributes referenced by the constraints are correctly associated with the Data Model attributes.

        :param data_model: the data model used to check the validity of the constraint expression.
        :return: ``True`` if the constraint expression is valid wrt the data model, ``False`` otherwise.
        """

    def _check_validity(self) -> None:
        """Check whether a Constraint Expression satisfies some basic requirements.
        E.g. an :class:`~oef.query.And` expression must have at least 2 subexpressions.

        :return ``None``
        :raises ValueError: if the object does not satisfy some requirements."""
        return

    @property
    @abstractmethod
    def _node(self):
        pass


class And(ConstraintExpr):
    """
    A constraint type that allows you to specify a conjunction of constraints.
    That is, the :class:`~oef.query.And` constraint is satisfied whenever
    all the constraints that constitute the and are satisfied.

    Examples:
        All the books whose title is between 'I' and 'J' (alphanumeric order) but not equal to 'It'

        >>> c = And([Constraint("title", Range(("I", "J"))), Constraint("title", NotEq("It"))])
        >>> c.check(Description({"title": "I, Robot"}))
        True
        >>> c.check(Description({"title": "It"}))
        False
        >>> c.check(Description({"title": "1984"}))
        False
    """

    def __init__(self, constraints: List[ConstraintExpr]) -> None:
        """
        Initialize an :class:`~oef.query.And` constraint.

        :param constraints: the list of constraints to be interpreted in conjunction.
        """
        self.constraints = constraints

        self.node = QueryBuildingBlocks.Branch(combiner=ProtoHelpers.COMBINER_ALL)
        for c in constraints:
            self.node.Add(c._node)

        self._check_validity()

    @property
    def _node(self):
        return self.node

    def check(self, description: Description) -> bool:
        """
        Check if a value satisfies the :class:`~oef.query.And` constraint expression.

        :param description: the description to check.
        :return: ``True`` if the description satisfy the constraint expression, ``False`` otherwise.
        """
        return all(expr.check(description) for expr in self.constraints)

    def is_valid(self, data_model: DataModel) -> bool:
        return all(c.is_valid(data_model) for c in self.constraints)

    def _check_validity(self):
        if len(self.constraints) < 2:
            raise ValueError("Invalid input value for type '{}': number of "
                             "subexpression must be at least 2.".format(type(self).__name__))
        for c in self.constraints:
            c._check_validity()

    def __eq__(self, other):
        if type(other) != And:
            return False
        else:
            return self.constraints == other.constraints


class Or(ConstraintExpr):
    """
    A constraint type that allows you to specify a disjunction of constraints.
    That is, the Or constraint is satisfied whenever at least one of the constraints
    that constitute the or is satisfied.

    Examples:
        All the books that have been published either before the year 1960 or after the year 1970

        >>> c = Or([Constraint("year", Lt(1960)), Constraint("year", Gt(1970))])
        >>> c.check(Description({"year": 1950}))
        True
        >>> c.check(Description({"year": 1975}))
        True
        >>> c.check(Description({"year": 1960}))
        False
        >>> c.check(Description({"year": 1970}))
        False
    """

    def __init__(self, constraints: List[ConstraintExpr]) -> None:
        """
        Initialize an :class:`~oef.query.Or` constraint.

        :param constraints: the list of constraints to be interpreted in disjunction.
        """
        self.constraints = constraints

        self.node = QueryBuildingBlocks.Branch(combiner=ProtoHelpers.COMBINER_ANY)
        for c in constraints:
            self.node.Add(c._node)

        self._check_validity()

    @property
    def _node(self):
        return self.node

    def check(self, description: Description) -> bool:
        """
        Check if a value satisfies the :class:`~oef.query.Or` constraint expression.

        :param description: the description to check.
        :return: ``True`` if the description satisfy the constraint expression, ``False`` otherwise.
        """
        return any(expr.check(description) for expr in self.constraints)

    def is_valid(self, data_model: DataModel) -> bool:
        return all(c.is_valid(data_model) for c in self.constraints)

    def _check_validity(self):
        if len(self.constraints) < 2:
            raise ValueError("Invalid input value for type '{}': number of "
                             "subexpression must be at least 2.".format(type(self).__name__))
        for c in self.constraints:
            c._check_validity()

    def __eq__(self, other):
        if type(other) != Or:
            return False
        else:
            return self.constraints == other.constraints


class Not(ConstraintExpr):
    """
    A constraint type that allows you to specify a negation of a constraint.
    That is, the Not constraint is satisfied whenever the constraint
    that constitutes the Not expression is not satisfied.

    Examples:
        All the books whose genre is science fiction, but the year is not between 1990 and 2000

        >>> c = And([Constraint("genre", Eq("science-fiction")), Not(Constraint("year", Range((1990, 2000))))])
        >>> c.check(Description({"genre": "science-fiction", "year": 1995}))
        False
        >>> c.check(Description({"genre": "science-fiction", "year": 2001}))
        True
    """

    def __init__(self, constraint: ConstraintExpr) -> None:
        self.constraint = constraint
        self.node = QueryBuildingBlocks.Branch(combiner=ProtoHelpers.COMBINER_NONE)
        self.node.Add(constraint._node)

    @property
    def _node(self):
        return self.node

    def check(self, description: Description) -> bool:
        """
        Check if a value satisfies the :class:`~oef.query.Not` constraint expression.

        :param description: the description to check.
        :return: ``True`` if the description satisfy the constraint expression, ``False`` otherwise.
        """
        return not self.constraint.check(description)

    def is_valid(self, data_model: DataModel) -> bool:
        return self.constraint.is_valid(data_model)

    def __eq__(self, other):
        if type(other) != Not:
            return False
        else:
            return self.constraint == other.constraint


class ConstraintType(ProtobufSerializable, ABC):
    """
    This class is used to represent a constraint type.
    """

    @abstractmethod
    def check(self, value: ATTRIBUTE_TYPES) -> bool:
        """
        Check if an attribute value satisfies the constraint.
        The implementation depends on the constraint type.

        :param value: the value to check.
        :return: ``True`` if the value satisfy the constraint, ``False`` otherwise.
        """

    def is_valid(self, attribute: AttributeSchema) -> bool:
        """
        Check if the constraint type is valid wrt a given attribute.

        :param attribute: the data model used to check the validity of the constraint type.
        :return: ``True`` if the constraint type is valid wrt the attribute, ``False`` otherwise.
        """
        return self._type is None or self._type == attribute.type

    def _check_validity(self) -> None:
        """Check whether a Constraint Expression satisfies some basic requirements.
        E.g. an :class:`~oef.query.And` expression must have at least 2 subexpressions.

        :return ``None``
        :raises ValueError: if the object does not satisfy some requirements."""
        return

    @property
    @abstractmethod
    def _operator(self) -> str:
        """The operator of the relation."""
        pass

    @property
    @abstractmethod
    def _type(self) -> str:
        pass

    @property
    @abstractmethod
    def _value(self):
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
    def _type(self) -> str:
        return ProtoHelpers.pythonTypeToString(self.value)

    @property
    def _value(self):
        return self.value

    def __eq__(self, other):
        if type(other) != type(self):
            return False
        else:
            return self.value == other.value


class OrderingRelation(Relation, ABC):
    """A specialization of the :class:`~oef.query.Relation` class to represent ordering relation (e.g. greater-than)."""

    def __init__(self, value: ORDERED_TYPES):
        super().__init__(value)


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

    @property
    def _operator(self):
        return ProtoHelpers.OPERATOR_EQ

    def check(self, value: ATTRIBUTE_TYPES) -> bool:
        """
        Check if a value is equal to the value of the constraint.

        :param value: the value to check.
        :return: ``True`` if the value satisfy the constraint, ``False`` otherwise.
        """
        return value == self.value


class NotEq(Relation):
    """
    The non-equality relation. That is, if the value of an attribute is not equal to the value specified then
    the :class:`~oef.query.Constraint` with this constraint type is satisfied.

    Examples:
        All the books that are not of the genre Horror

        >>> c = Constraint("genre", NotEq("horror"))
        >>> c.check(Description({"genre": "non-fiction"}))
        True
        >>> c.check(Description({"author": "horror"}))
        False

    """

    @property
    def _operator(self):
        return ProtoHelpers.OPERATOR_NE

    def check(self, value: ATTRIBUTE_TYPES) -> bool:
        """
        Check if a value is not equal to the value of the constraint.

        :param value: the value to check.
        :return: ``True`` if the value satisfy the constraint, ``False`` otherwise.
        """
        return value != self.value


class Lt(OrderingRelation):
    """
    The Less-than relation. That is, if the value of an attribute is less than the value specified then
    the :class:`~oef.query.Constraint` with this constraint type is satisfied.

    Examples:
        All the books published before 1990

        >>> c = Constraint("year", Lt(1990))
        >>> c.check(Description({"year": 1985}))
        True
        >>> c.check(Description({"year": 2000}))
        False

    """

    @property
    def _operator(self):
        return ProtoHelpers.OPERATOR_LT

    def check(self, value: ORDERED_TYPES) -> bool:
        """
        Check if a value is less than the value of the constraint.

        :param value: the value to check.
        :return: ``True`` if the value satisfy the constraint, ``False`` otherwise.
        """
        return value < self.value


class LtEq(OrderingRelation):
    """
    Less-than-equal relation. That is, if the value of an attribute is less than or equal to the value specified then
    the :class:`~oef.query.Constraint` with this constraint type is satisfied.

    Examples:
        All the books published before 1990, 1990 included

        >>> c = Constraint("year", LtEq(1990))
        >>> c.check(Description({"year": 1990}))
        True
        >>> c.check(Description({"year": 1991}))
        False

    """

    @property
    def _operator(self):
        return ProtoHelpers.OPERATOR_LE

    def check(self, value: ORDERED_TYPES) -> bool:
        """
        Check if a value is less than or equal to the value of the constraint.

        :param value: the value to check.
        :return: ``True`` if the value satisfy the constraint, ``False`` otherwise.
        """
        return value <= self.value


class Gt(OrderingRelation):
    """
    Greater-than relation. That is, if the value of an attribute is greater than the value specified then
    the :class:`~oef.query.Constraint` with this constraint type is satisfied.

    Examples:
        All the books with rating greater than 4.0

        >>> c = Constraint("average_rating", Gt(4.0))
        >>> c.check(Description({"average_rating": 4.5}))
        True
        >>> c.check(Description({"average_rating": 3.0}))
        False
    """

    @property
    def _operator(self):
        return ProtoHelpers.OPERATOR_GT

    def check(self, value: ORDERED_TYPES) -> bool:
        """
        Check if a value is greater than the value of the constraint.

        :param value: the value to check.
        :return: ``True`` if the value satisfy the constraint, ``False`` otherwise.
        """
        return value > self.value


class GtEq(OrderingRelation):
    """
    Greater-than-equal relation. That is, if the value of an attribute is greater than or equal to the value specified
    then the :class:`~oef.query.Constraint` with this constraint type is satisfied.

    Examples:
        All the books published after 2000, included

        >>> c = Constraint("year", GtEq(2000))
        >>> c.check(Description({"year": 2000}))
        True
        >>> c.check(Description({"year": 1990}))
        False
    """

    @property
    def _operator(self):
        return ProtoHelpers.OPERATOR_GE

    def check(self, value: ORDERED_TYPES) -> bool:
        """
        Check if a value greater than or equal to the value of the constraint.

        :param value: the value to check.
        :return: ``True`` if the value satisfy the constraint, ``False`` otherwise.
        """
        return value >= self.value


class Range(ConstraintType):
    """
    A constraint type that allows you to restrict the values of the attribute in a given range.

    Examples:
        All the books published after 2000, included

        >>> c = Constraint("year", Range((2000, 2005)))
        >>> c.check(Description({"year": 2000}))
        True
        >>> c.check(Description({"year": 2005}))
        True
        >>> c.check(Description({"year": 1990}))
        False
        >>> c.check(Description({"year": 2010}))
        False
    """

    def __init__(self, values: RANGE_TYPES) -> None:
        """
        Initialize a range constraint type.

        :param values: a pair of ``int``, a pair of ``str``, a pair of ``float` or
                     | a pair of :class:`~oef.schema.Location`.
        """
        self.values = values

    @property
    def _operator(self):
        return ProtoHelpers.OPERATOR_IN

    @property
    def _type(self) -> str:
        return ProtoHelpers.typeToRange(ProtoHelpers.pythonTypeToString(self.values[0]))

    @property
    def _value(self):
        return self.values

    def check(self, value: RANGE_TYPES) -> bool:
        """
        Check if a value is in the range specified by the constraint.

        :param value: the value to check.
        :return: ``True`` if the value satisfy the constraint, ``False`` otherwise.
        """
        left, right = self.values
        return left <= value <= right

    def _get_type(self) -> Type[Union[int, str, float, Location]]:
        return type(self.values[0])

    def __eq__(self, other):
        if type(other) != Range:
            return False
        else:
            return self.values == other.values


class Set(ConstraintType, ABC):
    """
    A constraint type that allows you to restrict the values of the attribute in a specific set.

    The specific operator of the relation is defined in the subclasses that extend this class.
    """

    def __init__(self, values: SET_TYPES) -> None:
        """
        Initialize a :class:`~oef.query.Set` constraint.

        :param values: a list of values for the set relation.
        """
        self.values = values

    @property
    def _type(self) -> str:
        return ProtoHelpers.typeToSet(ProtoHelpers.pythonTypeToString(next(iter(self.values))))

    @property
    def _value(self):
        return self.values

    def __eq__(self, other):
        if type(other) != type(self):
            return False
        return self.values == other.values


class In(Set):
    """
    Class that implements the 'in set' constraint type.
    That is, the value of attribute over which the constraint is defined
    must be in the set of values provided.

    Examples:
        All the books whose genre is one of the following: `Horror`, `Science fiction`, `Non-fiction`

        >>> c = Constraint("genre", In(["horror", "science fiction", "non-fiction"]))
        >>> c.check(Description({"genre": "horror"}))
        True
        >>> c.check(Description({"genre": "thriller"}))
        False

    """

    def __init__(self, values: SET_TYPES):
        super().__init__(values)

    @property
    def _operator(self):
        return ProtoHelpers.OPERATOR_IN

    def check(self, value: ATTRIBUTE_TYPES) -> bool:
        """
        Check if a value is in the set of values specified by the constraint.

        :param value: the value to check.
        :return: ``True`` if the value satisfy the constraint, ``False`` otherwise.
        """
        return value in self.values


class NotIn(Set):
    """
    Class that implements the 'not in set' constraint type.
    That is, the value of attribute over which the constraint is defined
    must be not in the set of values provided.

    Examples:
        All the books that have not been published neither in 1990, nor in 1995, nor in 2000

        >>> c = Constraint("year", NotIn([1990, 1995, 2000]))
        >>> c.check(Description({"year": 1991}))
        True
        >>> c.check(Description({"year": 2000}))
        False

    """

    def __init__(self, values: SET_TYPES):
        super().__init__(values)

    @property
    def _operator(self):
        return ProtoHelpers.OPERATOR_NOT_IN

    def check(self, value: ATTRIBUTE_TYPES) -> bool:
        """
        Check if a value is not in the set of values specified by the constraint.

        :param value: the value to check.
        :return: ``True`` if the value satisfy the constraint, ``False`` otherwise.
        """
        return value not in self.values


class Distance(ConstraintType):
    """
    Class that implements the 'distance' constraint type.
    That is, the locations we are looking for
    must be within a given distance from a given location.
    The distance is interpreted as a radius from a center.

    Examples:
        Define a location of interest, e.g. the Tour Eiffel
        >>> tour_eiffel = Location(48.8581064, 2.29447)

        Find all the locations close to the Tour Eiffel within 1 km
        >>> close_to_tour_eiffel = Distance(tour_eiffel, 1.0)

        Le Jules Verne, a famous restaurant close to the Tour Eiffel, satisfies the constraint.
        >>> le_jules_verne_restaurant = Location(48.8579675, 2.2951849)
        >>> close_to_tour_eiffel.check(le_jules_verne_restaurant)
        True

        The Colosseum does not satisfy the constraint (farther than 1 km from the Tour Eiffel).
        >>> colosseum = Location(41.8902102, 12.4922309)
        >>> close_to_tour_eiffel.check(colosseum)
        False

    """

    def __init__(self, center: Location, distance: float) -> None:
        """
        Instantiate the ``Distance`` constraint.

        :param center: the center from where compute the distance.
        :param distance: the maximum distance from the center, in km.
        """
        self.center = center
        self.distance = distance

    def check(self, value: Location) -> bool:
        return self.center.distance(value) <= self.distance

    @property
    def _type(self) -> str:
        return "DISTANCE"

    @property
    def _value(self):
        return self.center, self.distance

    def __eq__(self, other):
        if type(other) != Distance:
            return False
        return self.center == other.center and self.distance == other.distance


class Constraint(ConstraintExpr):
    """
    A class that represent a constraint over an attribute.
    """

    def __init__(self,
                 attribute_name: str,
                 constraint: ConstraintType) -> None:
        self.attribute_name = attribute_name
        self.constraint = constraint

        if constraint._type == "DISTANCE":
            location, distance = constraint._value
            loc = QueryBuildingBlocks.Leaf(
                operator=ProtoHelpers.OPERATOR_EQ,
                query_field_value=location,
                query_field_type="location",
                target_field_name=attribute_name + ".location",
            )
            rad = QueryBuildingBlocks.Leaf(
                operator=ProtoHelpers.OPERATOR_EQ,
                query_field_value=distance,
                query_field_type="double",
                target_field_name=attribute_name + ".radius",
            )
            self.node = QueryBuildingBlocks.Branch(combiner=ProtoHelpers.COMBINER_ALL)
            self.node.Add(loc)
            self.node.Add(rad)
        else:
            self.node = QueryBuildingBlocks.Leaf(
                operator=constraint._operator,
                query_field_value=constraint._value,
                query_field_type=constraint._type,
                target_field_name=attribute_name,
            )

    @property
    def _node(self):
        return self.node

    def check(self, description: Description) -> bool:
        """
        Check if a description satisfies the constraint. The implementation depends on the type of the constraint.

        :param description: the description to check.
        :return: ``True`` if the description satisfies the constraint, ``False`` otherwise.

        Examples:
            >>> attr_author = AttributeSchema("author" , str, True, "The author of the book.")
            >>> attr_year   = AttributeSchema("year",    int, True, "The year of publication of the book.")
            >>> c1 = Constraint("author", Eq("Stephen King"))
            >>> c2 = Constraint("year", Gt(1990))
            >>> book_1 = Description({"author": "Stephen King",  "year": 1991})
            >>> book_2 = Description({"author": "George Orwell", "year": 1948})

            The ``"author"`` attribute instantiation satisfies the constraint, so the result is ``True``.

            >>> c1.check(book_1)
            True

            Here, the ``"author"`` does not satisfy the constraints. Hence, the result is ``False``.

            >>> c1.check(book_2)
            False

            In this case, there is a missing field specified by the query, that is ``"year"``
            So the result is ``False``, even in the case it is not required by the schema:

            >>> c2.check(Description({"author": "Stephen King"}))
            False

            If the type of some attribute of the description is not correct, the result is ``False``.
            In this case, the field ``"year"`` has a string instead of an integer:

            >>> c2.check(Description({"author": "Stephen King", "year": "1991"}))
            False
            >>> Constraint("position", Distance(Location(0.0, 0.0), 1.0)).check(Description({"position": "1.0,1.0"}))
            False

        """
        # if the name of the attribute is not present, return false.
        name = self.attribute_name
        if name not in description.values:
            return False

        # if the type of the value is different from the type of the attribute schema, return false.
        value = description.values[name]
        if type(value) != self.constraint._get_type():
            return False

        # dispatch the check to the right implementation for the concrete constraint type.
        return self.constraint.check(value)

    def is_valid(self, data_model: DataModel) -> bool:
        # if the attribute name of the constraint is not present in the data model, the constraint is not valid.
        if self.attribute_name not in data_model.attributes_by_name:
            return False

        attribute = data_model.attributes_by_name[self.attribute_name]
        return self.constraint.is_valid(attribute)

    def __eq__(self, other):
        if type(other) != Constraint:
            return False
        else:
            return self.attribute_name == other.attribute_name and self.constraint == other.constraint


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

        self.root = QueryBuildingBlocks.Branch(combiner=ProtoHelpers.COMBINER_ALL)
        for c in constraints:
            self.root.Add(c._node)

        self._check_validity()

    def to_pb(self) -> dap_interface_pb2.ConstructQueryObjectRequest:
        """
        Return the associated Protobuf object.

        :return: a Protobuf object equivalent to the caller object.
        """
        return self.root.toProto()

    def check(self, description: Description) -> bool:
        """
        Check if a description satisfies the constraints of the query.
        The constraints are interpreted as conjunction.

        :param description: the description to check.
        :return: ``True`` if the description satisfies all the constraints, ``False`` otherwise.
        """
        return all(c.check(description) for c in self.constraints)

    def is_valid(self, data_model: DataModel) -> bool:
        """
        Given a data model, check whether the query is valid for that data model.

        :return: ``True`` if the query is compliant with the data model, ``False`` otherwise.
        """
        if data_model is None:
            return True

        return all(c.is_valid(data_model) for c in self.constraints)

    def _check_validity(self):
        """Check whether the :class:`~oef.query.Query` object is valid.

        :return ``None``
        :raises ValueError: if the query does not satisfy some sanity requirements."""

        if len(self.constraints) < 1:
            raise ValueError("Invalid input value for type '{}': empty list of constraints. The number of "
                             "constraints must be at least 1.".format(type(self).__name__))
        if not self.is_valid(self.model):
            raise ValueError("Invalid input value for type '{}': the query is not valid "
                             "for the given data model.".format(type(self).__name__))

    def __eq__(self, other):
        if type(other) != Query:
            return False
        return self.constraints == other.constraints and self.model == other.model

    def getGraph(self):
        return self.root.graphVisualization()[0]


class SearchResultItem:
    def __init__(self, public_key: str,
                 core_key : str,
                 core_addr: str,
                 core_port: int,
                 distance: int):
        self.public_key = public_key
        self.core_key  = core_key
        self.core_addr = core_addr
        self.core_port = core_port
        self.distance  = distance

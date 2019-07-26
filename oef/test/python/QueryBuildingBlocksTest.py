import unittest
import sys
from utils.src.python.Logging import has_logger

from oef.src.python import QueryBuildingBlocks
from protocol.src.python import ProtoHelpers


class LeafTest(unittest.TestCase):

    @has_logger
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)

    def testLeaf(self):
        leaf = QueryBuildingBlocks.Leaf(
            operator=ProtoHelpers.OPERATOR_EQ,
            query_field_value="HelloWorld",
            query_field_type="string",
            target_field_name="example_attribute",
        )

        self.info("Leaf: ", leaf.printable())

        self.info("Leaf Proto: ", leaf.toProto(""))

        assert True

    def testBranch(self):
        combiner = ProtoHelpers.COMBINER_ANY

        r = QueryBuildingBlocks.Branch(combiner=combiner)

        self.info("Branch empty: ", r.printable())
        leaf = QueryBuildingBlocks.Leaf(
            operator=ProtoHelpers.OPERATOR_EQ,
            query_field_value="HelloWorld",
            query_field_type="string",
            target_field_name="example_attribute",
        )

        r.Add(leaf)
        self.info("Branch with leaf: ", r.printable())

        self.info("Branch Proto: ", r.toProto(""))

        assert True

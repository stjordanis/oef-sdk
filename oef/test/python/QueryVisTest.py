import unittest
import sys
from utils.src.python.Logging import has_logger

from oef.src.python.query import Query, Constraint, And, Or, Eq, NotEq, Lt, Gt, Range, In, NotIn, Not


class QueryVisTest(unittest.TestCase):

    @has_logger
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)

    def testSimple(self):
        q = Query([
            Constraint("a", Eq(5)),
            Constraint("b", Lt(15)),
            Constraint("b", NotEq(5))
        ])

        g = q.getGraph()
        #g.view()
        assert True

    def testSimple2(self):
        a = And([Constraint("title", Range(("I", "J"))), Constraint("title", NotEq("It")),
                 Or([
                     Constraint("genre", In(["horror", "science fiction", "non-fiction"])),
                     Constraint("genre", In(["comedy"]))
                 ])])
        q = Query([
            Constraint("a", Eq(5)),
            Constraint("b", Lt(15)),
            Constraint("b", NotEq(5)),
            Not(a)
        ])
        #print(q.root.toProto())

        g = q.getGraph()
        g.view()
        assert True

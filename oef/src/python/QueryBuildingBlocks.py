from utils.src.python.Logging import has_logger
from protocol.src.proto import dap_interface_pb2
from protocol.src.python import TypeHelpers


class Branch(object):
    def __init__(self,
                 combiner=None,
                 **kwargs):
        self.leaves = []
        self.subnodes = []
        self.type = None
        self.combiner = combiner
        self.name = "?"
        self.mementos = []

        self.dap_names = set()  # the set of names of all responding daps.
        self.dap_field_candidates = {}  # A map of name->field info for every responder.

    def Clear(self):
        self.leaves = []
        self.subnodes = []

    def Copy(self):
        return Branch().fromProto(self.toProto())

    def printable(self):
        tablenames = [
            self.dap_field_candidates.get(x, {}).get('target_table_name', '<NONE>')
            for x in (self.dap_names or [])
        ]
        return "Branch {} -- \"{}\" over {} (tns={}) ({} children, {} leaves) mementos={}".format(
            self.name,
            self.combiner,
            self.dap_names or "NO_COMMON_DAPS",
            tablenames,
            len(self.subnodes),
            len(self.leaves),
            str(len(self.mementos)) + " MEMS" if self.mementos else "NO_MEM",
        )

    def print(self, depth=0):
        yield depth * "  " + self.printable()
        for n in self.subnodes:
            yield from n.print(depth + 1)
        for leaf in self.leaves:
            yield (depth + 1) * "  " + leaf.printable()

    def MergeDaps(self):

        dap_names = [
                        x.dap_names for x in self.subnodes
                    ] + [
                        x.dap_names for x in self.leaves
                    ]

        self.dap_names = set(dap_names[0]) if dap_names[0] else []
        for n in dap_names[1:]:
            if n != self.dap_names:
                self.dap_names = None
                break

        if self.dap_names == None:
            return

        merged_candidates = {}
        for candidate_to_merge in [
                                      x.dap_field_candidates for x in self.subnodes
                                  ] + [
                                      x.dap_field_candidates for x in self.leaves
                                  ]:
            merged_candidates.update(candidate_to_merge)

        # This is part of the HANDLE_MULTIPLE_DAPS_SIMPLY hack.
        self.dap_field_candidates = dict([
            (k, merged_candidates[k]) for k in self.dap_names
        ])

        # print("MergeDaps:", self.printable())

    def Add(self, new_child):
        if isinstance(new_child, Branch):
            self.subnodes.append(new_child)
        else:
            self.leaves.append(new_child)

    def fromProto(self, pb):
        self.combiner = pb.operator
        self.dap_names = set(pb.dap_names)
        if pb.HasField('node_name'):
            self.name = pb.node_name

        self.leaves = [Leaf().fromProto(x) for x in pb.constraints]
        self.subnodes = [Branch().fromProto(x) for x in pb.children]
        return self

    def toProto(self, dap_name):
        pb = dap_interface_pb2.ConstructQueryObjectRequest()
        pb.operator = self.combiner
        pb.dap_names.extend(list(self.dap_names))
        if self.name != None:
            pb.node_name = self.name

        for leaf in self.leaves:
            new_leaf = pb.constraints.add()
            new_leaf.CopyFrom(leaf.toProto(dap_name))

        for child in self.subnodes:
            new_child = pb.children.add()
            new_child.CopyFrom(child.toProto(dap_name))

        return pb


class Leaf(object):
    @has_logger
    def __init__(self,
                 operator=None,
                 query_field_type=None,
                 query_field_value=None,
                 target_field_type=None,
                 target_field_name=None,
                 target_table_name=None,
                 dap_names=set(),
                 dap_field_candidates={},
                 **kwargs
                 ):
        self.operator = operator

        self.query_field_value = query_field_value
        self.query_field_type = query_field_type

        self.target_field_name = target_field_name
        self.target_field_type = target_field_type
        self.target_table_name = target_table_name

        self.dap_field_candidates = dap_field_candidates  # A map of name->field info for every responder.
        self.dap_names = dap_names  # the set of names of all responding daps.
        self.name = "?"
        self.mementos = []

    def toProto(self, dap_name):
        pb = dap_interface_pb2.ConstructQueryConstraintObjectRequest()

        v = TypeHelpers.encodeConstraintValue(self.query_field_value, self.query_field_type, self.log)

        pb.query_field_value.CopyFrom(v)
        pb.node_name = self.name

        pb.operator = self.operator
        pb.query_field_type = self.query_field_type
        pb.target_field_name = self.target_field_name

        pb.target_field_type = self.dap_field_candidates.get(dap_name, {}).get('target_field_type', "")
        pb.target_table_name = self.dap_field_candidates.get(dap_name, {}).get('target_table_name', "")

        pb.dap_name = dap_name

        if self.name != None:
            pb.node_name = self.name
        return pb

    def fromProto(self, pb):
        self.query_field_value = TypeHelpers.decodeConstraintValue(pb.query_field_value)
        self.operator = pb.operator
        self.query_field_type = pb.query_field_type
        self.target_field_name = pb.target_field_name
        self.target_field_type = pb.target_field_type
        self.target_table_name = pb.target_table_name
        self.dap_name = pb.dap_name
        if pb.HasField('node_name'):
            self.name = pb.node_name
        return self

    def printable(self):
        return "Leaf {} -- daps={} {} {} {} (type={}) mementos={}".format(
            self.name,
            self.dap_names,
            self.target_field_name,
            self.operator,
            self.query_field_value if self.query_field_type != 'data_model' else "DATA_MODEL",
            self.query_field_type,
            str(len(self.mementos)) + " MEMS" if self.mementos else "NO_MEM",
        )
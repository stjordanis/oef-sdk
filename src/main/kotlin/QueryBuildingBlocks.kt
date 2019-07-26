package ai.fetch.oef

import com.google.protobuf.Message

interface TargetFieldNameUpdateInterface {
    fun updateTargetFieldName(name: String)
}

interface Node : TargetFieldNameUpdateInterface {
    fun toProto(): Message
}

class Branch(
    var name: String = "?",
    var combiner: String = ""
) : Node {
    val leaves: MutableList<Leaf> = arrayListOf()
    val subnodes: MutableList<Branch> = arrayListOf()


    fun Clear() {
        leaves.clear()
        subnodes.clear()
    }

    fun Add(child: Node) {
        when(child) {
            is Branch -> subnodes.add(child)
            is Leaf   -> leaves.add(child)
        }
    }

    override fun toString(): String {
        return "Branch $name -- \"$combiner\" (${subnodes.size} children, ${leaves.size} leaves)"
    }

    fun print(depth: Int = 0): Sequence<String> = sequence {
        yield(" ".repeat(depth)+toString())
        for(n in subnodes){
            yieldAll(n.print(depth+1))
        }
        for(leaf in leaves) {
            yield(" ".repeat(depth+1)+leaf.toString())
        }
    }

    override fun toProto(): DapInterface.ConstructQueryObjectRequest = DapInterface.ConstructQueryObjectRequest.newBuilder()
        .apply {
            operator = combiner
            nodeName = name
            for(leaf in leaves) {
                addConstraints(leaf.toProto())
            }
            for(child in subnodes) {
                addChildren(child.toProto())
            }
        }
        .build()

    override fun updateTargetFieldName(name: String) {
    }

    companion object {
        fun fromProto(pb: DapInterface.ConstructQueryObjectRequest): Branch = Branch(pb.nodeName, pb.operator).apply {
            leaves.addAll(pb.constraintsList.map {
                Leaf.fromProto(it)
            })
            subnodes.addAll(pb.childrenList.map {
                fromProto(it)
            })
        }
    }
}

class Leaf(
    var name: String = "?",
    var operator: String = "",
    var queryFieldType: String = "",
    var queryFieldValue: DapInterface.ValueMessage? = null,
    var targetFieldType: String = "",
    var targetFieldName: String = "",
    var targetTableName: String = ""
) : Node {
    override fun toString(): String = "Leaf $name -- $targetFieldName $operator ${if (queryFieldType =="data_model") "DATA_MODEL" else queryFieldValue} (type=$queryFieldType)"

    override fun toProto(): DapInterface.ConstructQueryConstraintObjectRequest =  DapInterface.ConstructQueryConstraintObjectRequest.newBuilder()
        .also {
            it.nodeName = name
            it.operator = operator
            it.queryFieldType = queryFieldType
            it.queryFieldValue = queryFieldValue
            it.targetFieldType = targetFieldType
            it.targetFieldName = targetFieldName
            it.targetTableName = targetTableName
        }
        .build()

    override fun updateTargetFieldName(name: String) {
        targetFieldName = name
    }

    companion object {
        fun fromProto(pb: DapInterface.ConstructQueryConstraintObjectRequest) = Leaf(
            pb.nodeName,
            pb.operator,
            pb.queryFieldType,
            pb.queryFieldValue,
            pb.targetFieldType,
            pb.targetFieldName,
            pb.targetTableName
        )
    }
}
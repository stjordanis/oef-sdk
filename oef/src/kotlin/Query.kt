/**
 * Copyright 2018 Fetch.AI Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.fetch.oef

import fetch.oef.pb.QueryOuterClass.Query as QueryPb


/**
 * This class is used to represent a constraint expression.
 */
sealed class ConstraintExpr {
    protected val node: Branch = Branch()

    protected fun addConstraints(constraints: List<ConstraintExpr>)
    {
        constraints.forEach {
            node.Add(it.getNode())
        }
    }

    open fun getNode(): Node = node
}

/**
 * A constraint type that allows you to specify a conjunction of constraints.
 * That is, the [And] constraint is satisfied whenever
 * all the constraints that constitute the and are satisfied.
 *
 * Example:
 * All the books whose title is between 'I' and 'J' (alphanumeric order) but not equal to 'It'
 * ```
 * val c = And(listOf(Constraint("title", Range.StringPair(("I", "J"))), Constraint("title", Relation.NOTEQ("It"))))
 * ```
 *
 * @property constraints the list of constraints to be interpreted in conjunction
 */
class And(val constraints: List<ConstraintExpr>) : ConstraintExpr() {
    init {
        node.apply {
            name = "AND"
            combiner = COMBINER_ALL
            addConstraints(constraints)
        }
    }
}

/**
 * A constraint type that allows you to specify a disjunction of constraints.
 * That is, the Or constraint is satisfied whenever at least one of the constraints
 * that constitute the or is satisfied.
 *
 * Example:
 * All the books that have been published either before the year 1960 or after the year 1970
 * ```
 * val c = Or(listOf(Constraint("year", Relation.LT(1960)), Constraint("year", Relation.GT(1970))))
 * ```
 *
 * @property constraints the list of constraints to be interpreted in disjunction.
 */
class Or(val constraints: List<ConstraintExpr>)  : ConstraintExpr() {
    init {
        node.apply {
            name = "OR"
            combiner = COMBINER_ANY
            addConstraints(constraints)
        }
    }
}

/**
 * A constraint type that allows you to specify a negation of a constraint.
 * That is, the Not constraint is satisfied whenever the constraint that constitutes the Not expression is not satisfied.
 *
 * Example:
 * All the books whose genre is science fiction, but the year is not between 1990 and 2000
 * ```
 * val c = And(listOf(Constraint("genre", Relation.EQ("science-fiction")), Not(Constraint("year", Range.IntPair((1990, 2000))))))
 * ```
 *
 */
class Not(val constraint: ConstraintExpr) : ConstraintExpr() {
    init {
        node.apply {
            name = "NOT"
            combiner = COMBINER_NONE
            addConstraints(listOf(constraint))
        }
    }
}

/**
 * This class is used to represent a constraint type.
 */
sealed class ConstraintType<T : Node> (val node: T) {
    open fun updateTargetFieldName(name: String) {
        node.updateTargetFieldName(name)
    }
}

/**
 *  A constraint type that allows you to restrict the values of the attribute in a given range.
 *
 *  Examples:
 *  All the books published after 2000, included
 *  ```
 *  val c = Constraint("year", Range(2000, 2005))
 *  ```
 */
sealed class Range<T> (val first: T, val second: T) : ConstraintType<Leaf>(Leaf()) {

    init {
        node.apply {
            operator = OPERATOR_IN
            queryFieldValue = DapInterface.ValueMessage
                .newBuilder()
                .fromKotlinType(first, second)
                .build()
            when(first) {
                is Int      -> queryFieldType = TYPE_INT32
                is Long     -> queryFieldType = TYPE_INT64
                is String   -> queryFieldType = TYPE_STRING
                is Double   -> queryFieldType = TYPE_DOUBLE
                is Float    -> queryFieldType = TYPE_FLOAT
                is Location -> queryFieldType = TYPE_LOCATION
            }
        }
    }
}


/**
 * A constraint type that allows you to impose specific values for the attributes.
 * The specific operator of the relation is defined in the subclasses that extend this class.
 *
 * @param value the right value of the relation.
 */
sealed class Relation<T> (value: T) : ConstraintType<Leaf>(Leaf()) {

    init {
        node.queryFieldValue = DapInterface.ValueMessage
            .newBuilder()
            .fromKotlinType(value)
            .build()
    }

    /**
     * The equality relation. That is, if the value of an attribute is equal to the value specified then the
     * [Constraint] with this constraint type is satisfied.
     *
     * Example:
     *      All the books whose author is Stephen King
     *      ```
     *      val c = Constraint("author", EQ("Stephen King"))
     *      ```
     */
    class EQ<T>(value: T)    : Relation<T>(value) {
        init {
            node.operator = OPERATOR_EQ
        }
    }

    /**
     * The Less-than relation. That is, if the value of an attribute is less than the value specified then
     * the [Constraint] with this constraint type is satisfied.
     *
     * Example:
     *      All the books published before 1990
     *      ```
     *      val c = Constraint("year", LT(1990))
     *      ```
     */
    class LT<T>(value: T)    : Relation<T>(value) {
        init {
            node.operator = OPERATOR_LT
        }
    }

    /**
     * Less-than-equal relation. That is, if the value of an attribute is less than or equal to the value specified then
     * the [Constraint] with this constraint type is satisfied.
     *
     * Example:
     *      All the books published before 1990, 1990 included
     *      ```
     *      val c = Constraint("year", LTEQ(1990))
     *      ```
     */
    class LTEQ<T>(value: T)  : Relation<T>(value) {
        init {
            node.operator = OPERATOR_LE
        }
    }

    /**
     * Greater-than relation. That is, if the value of an attribute is greater than the value specified then the
     * [Constraint] with this constraint type is satisfied.
     *
     * Example:
     *      All the books with rating greater than 4.0
     *      ```
     *      val c = Constraint("average_rating", GT(4.0))
     *      ```
     */
    class GT<T>(value: T)    : Relation<T>(value) {
        init {
            node.operator = OPERATOR_GT
        }
    }

    /**
     *  Greater-than-equal relation. That is, if the value of an attribute is greater than or equal to the value
     *  specified then the [Constraint] with this constraint type is satisfied.
     *
     * Example:
     *      All the books published after 2000, included
     *      ```
     *      val c = Constraint("year", GTEQ(2000))
     *      ```
     */
    class GTEQ<T>(value: T)  : Relation<T>(value) {
        init {
            node.operator = OPERATOR_GE
        }
    }

    /**
     * The non-equality relation. That is, if the value of an attribute is not equal to the value specified then
     * the [Constraint] with this constraint type is satisfied.
     *
     * Example:
     *      All the books that are not of the genre Horror
     *      ```
     *      val c = Constraint("genre", NOTEQ("horror"))
     *      ```
     */
    class NOTEQ<T>(value: T) : Relation<T>(value) {
        init {
            node.operator = OPERATOR_NE
        }
    }
}


/**
 * A constraint type that allows you to restrict the values of the attribute in a specific set.
 * The specific operator of the relation is defined in the subclasses that extend this class.
 */
sealed class Set<T> (
    values: List<T>,
    operation: String
) : ConstraintType<Leaf>(Leaf()) {

    init {
        node.operator = operation
        node.queryFieldValue = DapInterface.ValueMessage
            .newBuilder()
            .fromKotlinList(values)
            .build()
    }

    /**
     *  Class that implements the 'in set' constraint type.
     *  That is, the value of attribute over which the constraint is defined must be in the set of values provided.
     *
     *  Examples:
     *      All the books whose genre is one of the following: `Horror`, `Science fiction`, `Non-fiction`
     *      ```
     *      val c = Constraint("genre", Set.In(listOf("horror", "science fiction", "non-fiction")))
     *      ```
     */
    class IN<T>(values: List<T>)    : Set<T>(values, OPERATOR_IN)

    /**
     *  Class that implements the 'not in set' constraint type.
     *  That is, the value of attribute over which the constraint is defined must be not in the set of values provided.
     *
     *  Examples:
     *      All the books that have not been published neither in 1990, nor in 1995, nor in 2000
     *      val c = Constraint("year", Set.NOTIN(listOf(1990, 1995, 2000)))
     */
    class NOTIN<T>(values: List<T>) : Set<T>(values, OPERATOR_NOT_IN)

}


/**
 * Class that implements the 'distance' constraint type. That is, the locations we are looking for must be within
 * a given distance from a given location. The distance is interpreted as a radius from a center.
 *
 * Examples:
 *      Define a location of interest, e.g. the Tour Eiffel
 *      ```
 *      val tour_eiffel = Location(48.8581064, 2.29447)
 *      ```
 *
 *       Find all the locations close to the Tour Eiffel within 1 km
 *       ```
 *       close_to_tour_eiffel = Distance(tour_eiffel, 1.0)
 *       ```
 *
 * @param center the center from where compute the distancee
 * @param distance the maximum distance from the center, in m
 */
data class Distance (
    val center: Location,
    val distance: Double
) : ConstraintType<Branch>(Branch("distance", COMBINER_ALL)) {

    private val loc = Leaf(
        name = "location",
        operator = OPERATOR_EQ,
        queryFieldValue = DapInterface.ValueMessage.newBuilder().fromKotlinType(center).build(),
        queryFieldType = TYPE_LOCATION,
        targetFieldName = ".location"
    )
    private val rad = Leaf(
        name = "rad",
        operator =  OPERATOR_EQ,
        queryFieldValue = DapInterface.ValueMessage.newBuilder().fromKotlinType(distance).build(),
        queryFieldType = TYPE_DOUBLE,
        targetFieldName = ".radius"
    )

    init {
        node.Add(loc)
        node.Add(rad)
    }

    override fun updateTargetFieldName(name: String) {
        loc.targetFieldName = name + loc.targetFieldName
        rad.targetFieldName = name + rad.targetFieldName
    }
}

/**
 *  A class that represent a constraint over an attribute.
 */
class Constraint (
    val attributeName: String,
    val constraint: ConstraintType<*>
): ConstraintExpr() {

    init {
        constraint.updateTargetFieldName(attributeName)
    }

    override fun getNode(): Node = constraint.node
}

/**
 *  Representation of a search that is to be performed. Currently a search is represented as a
 *  set of key value pairs that must be contained in the description of the service/ agent.
 *
 *  Examples:
 *
 *  Return all the books written by Stephen King published after 1990, and available as an e-book:
 *  ```
 *  val attr_author   = AttributeSchema("author" ,         str,   True,  "The author of the book.")
 *  val attr_year     = AttributeSchema("year",            int,   True,  "The year of publication of the book.")
 *  val attr_ebook    = AttributeSchema("ebook_available", bool,  False, "If the book can be sold as an e-book.")
 *
 *  q = Query(listOf(
 *      Constraint("author", Relation.EQ("Stephen King")),
 *      Constraint("year", Relation.GT(1990)),
 *      Constraint("ebook_available", Relation.EQ(True))
 *      ))
 * ```
 */
class Query @JvmOverloads constructor(
    private val constraints: List<ConstraintExpr>,
    private val model: DataModel? = null
) {

    constructor() : this(listOf<Constraint>())

    private val root = Branch(combiner = COMBINER_ALL).apply {
        constraints.forEach {
            Add(it.getNode())
        }
        model?.let {
            Add(Leaf(
                operator = OPERATOR_CLOSE_TO,
                queryFieldType = TYPE_DATA_MODEL,
                queryFieldValue = DapInterface.ValueMessage
                    .newBuilder()
                    .setDm(DapInterface.ValueMessage.DataModel.parseFrom(it.toProto().toByteArray()))
                    .build()
            ))
        }
    }

    /**
     *   Return the associated Protobuf object.
     *
     *   @return a Protobuf object equivalent to the caller object.
     */
    fun toProto() = root.toProto()
}
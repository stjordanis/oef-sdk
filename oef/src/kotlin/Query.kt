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

import fetch.oef.pb.QueryOuterClass
import fetch.oef.pb.QueryOuterClass.Query as QueryPb


/**
 * This class is used to represent a constraint expression.
 */
sealed class ConstraintExpr {

    /**
     * Transforms [ConstraintExpr] object to OEF protocol buffer message.
     */
    fun toProtoConstraintExpr(): QueryPb.ConstraintExpr = QueryPb.ConstraintExpr.newBuilder()
        .also {
            when (this) {
                is And        -> it.and        = toProto()
                is Or         -> it.or         = toProto()
                is Not        -> it.not        = toProto()
                is Constraint -> it.constraint = toProto()
            }
        }.build()

    companion object {
        /**
         * Builds [ConstraintExpr] object from protocol buffer message.
         */
        fun fromProto(obj: QueryPb.ConstraintExpr): ConstraintExpr  = when {
            obj.hasAnd()        -> And.fromProto(obj.and)
            obj.hasOr()         -> Or.fromProto(obj.or)
            obj.hasNot()        -> Not.fromProto(obj.not)
            obj.hasConstraint() -> Constraint.fromProto(obj.constraint)
            else -> {
                throw UnknownTypeException("Unknown ConstraintType!")
            }
        }
    }
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
    /**
     * Transforms [And] object to OEF protocol buffer message.
     */
    fun toProto(): QueryPb.ConstraintExpr.And =  QueryPb.ConstraintExpr.And.newBuilder()
        .also {
            constraints.forEach { constraint->
                it.addExpr(constraint.toProtoConstraintExpr())
            }
        }
        .build()

    companion object {
        /**
         * Builds [And] object from protocol buffer message.
         */
        @Suppress("RemoveRedundantQualifierName")
        fun fromProto(obj: QueryPb.ConstraintExpr.And): And = And(obj.exprList.map { ConstraintExpr.fromProto(it) })
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
    /**
     * Transforms [Or] object to OEF protocol buffer message.
     */
    fun toProto(): QueryPb.ConstraintExpr.Or = QueryPb.ConstraintExpr.Or.newBuilder()
        .also {
            constraints.forEach { constraint->
                it.addExpr(constraint.toProtoConstraintExpr())
            }
        }
        .build()

    companion object {
        /**
         * Builds [Or] object from protocol buffer message.
         */
        @Suppress("RemoveRedundantQualifierName")
        fun fromProto(obj: QueryPb.ConstraintExpr.Or): Or = Or(obj.exprList.map { ConstraintExpr.fromProto(it) })
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
    /**
     * Transforms [Not] object to OEF protocol buffer message.
     */
    fun toProto(): QueryPb.ConstraintExpr.Not = QueryPb.ConstraintExpr.Not.newBuilder()
        .also {
            it.expr = constraint.toProtoConstraintExpr()
        }
        .build()

    companion object {
        /**
         * Builds [Not] object from protocol buffer message.
         */
        @Suppress("RemoveRedundantQualifierName")
        fun fromProto(pb: QueryPb.ConstraintExpr.Not) = Not(ConstraintExpr.fromProto(pb.expr))
    }
}

/**
 * This class is used to represent a constraint type.
 */
sealed class ConstraintType {
    /**
     * Transforms the Constraint into protobuf and attaches it to the given protocol buffer.
     *
     * @param pb The message which should be extended with the new contstraint.
     */
    fun extendProto(pb:  QueryPb.ConstraintExpr.Constraint.Builder) = pb.also {
        when (this) {
            is Range -> it.range = toProto()
            is Relation -> it.relation = toProto()
            is Set -> it.set = toProto()
            is Distance -> it.distance = toProto()
        }
    }

    companion object {
        /**
         * Creates Constraint object from protocol buffer.
         */
        fun fromProto(obj: QueryPb.ConstraintExpr.Constraint): ConstraintType = when {
            obj.hasSet()      -> Set.fromProto(obj.set)
            obj.hasRange()    -> Range.fromProto(obj.range)
            obj.hasRelation() -> Relation.fromProto(obj.relation)
            obj.hasDistance() -> Distance.fromProto(obj.distance)
            else -> {
                throw UnknownTypeException("Unknown Constraint!")
            }
        }
    }
}

/**
 *  A constraint type that allows you to restrict the values of the attribute in a given range.
 *
 *  Examples:
 *  All the books published after 2000, included
 *  ```
 *  val c = Constraint("year", Range.IntPair((2000, 2005)))
 *  ```
 */
sealed class Range : ConstraintType() {
    /**
     * Type to create a range between two stings.
     * @param first start string
     * @param second end string
     */
    data class StringPair  (val first: String,   val second: String)   : Range()

    /**
     * Type to create an integer range.
     * @param fist the integer the range should start from
     * @param second the end of the integer range
     */
    data class IntPair     (val first: Long,     val second: Long  )   : Range()
    /**
     * Type to create an double range.
     * @param fist the double the range should start from
     * @param second the end of the double range
     */
    data class DoublePair  (val first: Double,   val second: Double)   : Range()
    /**
    * Type to create an location range.
    * @param fist the location the range should start from
    * @param second the end of the location range
    */
    data class LocationPair(val first: Location, val second: Location) : Range()

    /**
     * Transforms [Range] object to OEF protocol buffer message.
     */
    fun toProto(): QueryPb.Range = QueryPb.Range.newBuilder()
        .also {
            when(this){
                is StringPair   -> it.setS(QueryPb.StringPair.newBuilder().also {sp->
                    sp.first  = first
                    sp.second = second
                })
                is IntPair      -> it.setI(QueryPb.IntPair.newBuilder().also {ip->
                    ip.first  = first
                    ip.second = second
                })
                is DoublePair   -> it.setD(QueryPb.DoublePair.newBuilder().also {dp->
                    dp.first  = first
                    dp.second = second
                })
                is LocationPair -> it.setL(QueryPb.LocationPair.newBuilder().also { lp->
                    lp.first = first.toProto()
                    lp.second = second.toProto()
                })
            }
        }
        .build()

    companion object {
        /**
         * Creates Range object from protocol buffer.
         */
        fun fromProto(obj: QueryPb.Range) = when {
            obj.hasS() -> StringPair(obj.s.first, obj.s.second)
            obj.hasI() -> IntPair   (obj.i.first, obj.i.second)
            obj.hasD() -> DoublePair(obj.d.first, obj.d.second)
            obj.hasL() -> LocationPair(Location.fromProto(obj.l.first), Location.fromProto(obj.l.second))
            else -> {
                throw UnknownTypeException("Unexpected Range type! ")
            }
        }
        /**
         * Creates Range object from Kotlin types.
         * Example:
         * Create integer range
         * ```
         * val r = Range.fromKotlinType(4,5)
         * ```
         * Create double range
         * ```
         * val r = Range.fromKotlinType(4.0,5.0)
         * ```
         * Create Location range
         * ```
         * val r = Range.fromKotlinType(Location(51.5098,-0.11809),Location(52.5098,-0.11809))
         * ```
         */
        fun <T> fromKotlinType(first: T, second: T) = when(first) {
            is Int      -> IntPair(first.toLong(), (second as Int).toLong())
            is Long     -> IntPair(first, second as Long)
            is String   -> StringPair(first, second as String)
            is Double   -> DoublePair(first, second as Double)
            is Float    -> DoublePair(first.toDouble(), (second as Float).toDouble())
            is Location -> LocationPair(first, second as Location)
            else -> {
                throw UnknownTypeException("Unsupported Range type!")
            }
        }
    }
}

/**
 * Generic function for creating a range objects from the arguments.
 *
 * Supported types: [Int], [Long], [String], [Double], [Float], [Location]
 *
 * @param first the first value of the range. Needs to be from the supported types.
 * @param second the second value of the range. Needs to be the same type as [first]

 */
fun <T> createRange(first: T, second: T): Range = Range.fromKotlinType(first, second)


/**
 * A constraint type that allows you to impose specific values for the attributes.
 * The specific operator of the relation is defined in the subclasses that extend this class.
 *
 * @param value the right value of the relation.
 */
sealed class Relation (val value: Value) : ConstraintType() {

    protected abstract fun getPbEnumType(): QueryPb.Relation.Operator

    /**
     * From an instance of Relation to its associated Protobuf object.
     * @return the Protobuf object that contains the relation.
     */
    fun toProto(): QueryPb.Relation = QueryPb.Relation.newBuilder()
        .setOp(getPbEnumType())
        .setVal(value.toProto())
        .build()

    companion object {
        /**
         * From the Relation Protobuf object to the associated instance of a subclass of Relation.
         *
         * @param obj the Protobuf object that represents the relation constraint.
         * @return: an instance of one of the subclasses of Relation.
         */
        fun fromProto(obj: QueryPb.Relation): Relation = when(obj.op){
            QueryPb.Relation.Operator.EQ    -> EQ  (obj.`val`)
            QueryPb.Relation.Operator.LT    -> LT  (obj.`val`)
            QueryPb.Relation.Operator.LTEQ  -> LTEQ(obj.`val`)
            QueryPb.Relation.Operator.GT    -> GTEQ(obj.`val`)
            QueryPb.Relation.Operator.GTEQ  -> GTEQ(obj.`val`)
            QueryPb.Relation.Operator.NOTEQ -> NOTEQ(obj.`val`)
            else -> throw UnsupportedOperationException("Operation ${obj.op} not supported!")
        }
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
    class EQ<T>(value: T)    : Relation(Value.fromKotlinType(value)) {
        override fun getPbEnumType() = QueryOuterClass.Query.Relation.Operator.EQ
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
    class LT<T>(value: T)    : Relation(Value.fromKotlinType(value)) {
        override fun getPbEnumType() = QueryOuterClass.Query.Relation.Operator.LT
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
    class LTEQ<T>(value: T)  : Relation(Value.fromKotlinType(value)) {
        override fun getPbEnumType() = QueryOuterClass.Query.Relation.Operator.LTEQ
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
    class GT<T>(value: T)    : Relation(Value.fromKotlinType(value)) {
        override fun getPbEnumType() = QueryOuterClass.Query.Relation.Operator.GT
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
    class GTEQ<T>(value: T)  : Relation(Value.fromKotlinType(value)) {
        override fun getPbEnumType() = QueryOuterClass.Query.Relation.Operator.GTEQ
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
    class NOTEQ<T>(value: T) : Relation(Value.fromKotlinType(value)) {
        override fun getPbEnumType() = QueryOuterClass.Query.Relation.Operator.NOTEQ
    }
}


/**
 * A constraint type that allows you to restrict the values of the attribute in a specific set.
 * The specific operator of the relation is defined in the subclasses that extend this class.
 */
sealed class Set (
    protected var values: Values,
    protected val operation: QueryPb.Set.Operator
) : ConstraintType() {
    protected sealed class Values {
        /**
         * Class representing set of [Long]s.
         */
        data class Ints     (val values: List<Long>)     : Values()
        /**
         * Class representing set of [Double]s.
         */
        data class Doubles  (val values: List<Double>)   : Values()
        /**
         * Class representing set of [String]s.
         */
        data class Strings  (val values: List<String>)   : Values()
        /**
         * Class representing set of [Boolean]s.
         */
        data class Bools    (val values: List<Boolean>)  : Values()
        /**
         * Class representing set of [Location]s.
         */
        data class Locations(val values: List<Location>) : Values()

        /**
         * From an instance of one of the subclasses of [Values] to its associated Protobuf object.
         *
         * @return the Protobuf object that contains the set constraint.
         */
        fun toProto(): QueryPb.Set.Values = QueryPb.Set.Values.newBuilder()
            .also {
                when (this) {
                    is Ints      -> it.i = QueryPb.Set.Values.Ints.newBuilder().addAllVals(values).build()
                    is Doubles   -> it.d = QueryPb.Set.Values.Doubles.newBuilder().addAllVals(values).build()
                    is Strings   -> it.s = QueryPb.Set.Values.Strings.newBuilder().addAllVals(values).build()
                    is Bools     -> it.b = QueryPb.Set.Values.Bools.newBuilder().addAllVals(values).build()
                    is Locations -> it.l = QueryPb.Set.Values.Locations.newBuilder().addAllVals(values.map { it.toProto() }).build()
                }
            }
            .build()

        companion object {
            /**
             * From the Set.Values Protobuf object to the associated instance of a subclass of [Values].
             *
             * @param obj the Protobuf object that represents the set constraint.
             * @return the object of one of the subclasses of [Values].
             */
            fun fromProto(obj: QueryPb.Set.Values): Values = when {
                obj.hasI() -> Ints(obj.i.valsList)
                obj.hasD() -> Doubles(obj.d.valsList)
                obj.hasS() -> Strings(obj.s.valsList)
                obj.hasB() -> Bools(obj.b.valsList)
                obj.hasL() -> Locations(obj.l.valsList.map { Location.fromProto(it) })
                else -> {
                    throw UnknownTypeException("Values type not supported!")
                }
            }
            internal fun <T> fromKotlinType(values: List<T>): Values {
                val v0: T = values[0]
                return when(v0) {
                    is Int      -> Ints     (values.map { (it as Int).toLong() })
                    is Long     -> Ints     (values.map { it as Long})
                    is Double   -> Doubles  (values.map { it as Double })
                    is Float    -> Doubles  (values.map { (it as Float).toDouble() })
                    is String   -> Strings  (values.map { it as String })
                    is Boolean  -> Bools    (values.map { it as Boolean })
                    is Location -> Locations(values.map { it as Location })
                    else -> {
                        throw UnknownTypeException("Type not supported!")
                    }
                }
            }
        }
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
    class IN<T>(values: List<T>)    : Set(Values.fromKotlinType(values), QueryPb.Set.Operator.IN)

    /**
     *  Class that implements the 'not in set' constraint type.
     *  That is, the value of attribute over which the constraint is defined must be not in the set of values provided.
     *
     *  Examples:
     *      All the books that have not been published neither in 1990, nor in 1995, nor in 2000
     *      val c = Constraint("year", Set.NOTIN(listOf(1990, 1995, 2000)))
     */
    class NOTIN<T>(values: List<T>) : Set(Values.fromKotlinType(values), QueryPb.Set.Operator.NOTIN)

    /**
     * From an instance of one of the subclasses of [Set] to its associated Protobuf object.
     *
     * @return the Protobuf object that contains the set constraint.
     */
    fun toProto(): QueryPb.Set = QueryPb.Set.newBuilder()
        .setOp(operation)
        .setVals(values.toProto())
        .build()

    protected fun changeValues(values: Values) {
        this.values = values
    }

    companion object {
        /**
         * From the Set Protobuf object to the associated instance of a subclass of [Set].
         *
         * @param obj the Protobuf object that represents the set constraint.
         * @return the object of one of the subclasses of [Set].
         */
        fun fromProto(obj: QueryPb.Set): Set = when(obj.op) {
            QueryPb.Set.Operator.IN    -> IN(listOf<Int>()).apply    { changeValues(Values.fromProto(obj.vals)) }
            QueryPb.Set.Operator.NOTIN -> NOTIN(listOf<Int>()).apply { changeValues(Values.fromProto(obj.vals)) }
            else -> {
                throw UnknownTypeException("Set operation type not supported!")
            }
        }
    }

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
) : ConstraintType() {

    /**
     * From an instance [Distance] to its associated Protobuf object.
     * @return the Protobuf object that contains the [Distance] constraint.
     */
    fun toProto(): QueryPb.Distance = QueryPb.Distance.newBuilder().also {
        it.center = center.toProto()
        it.distance = distance
    }.build()

    companion object {
        /**
         * From the Distance Protobuf object to the associated instance of [Distance].
         *
         * @param pb the Protobuf object that represents the [Distance]` constraint.
         * @return an instance of [Distance].
         */
        fun fromProto(pb: QueryPb.Distance) = Distance(Location.fromProto(pb.center), pb.distance)
    }
}

/**
 *  A class that represent a constraint over an attribute.
 */
class Constraint (
    val attributeName: String,
    val constraint: ConstraintType
): ConstraintExpr() {
    /**
     * Return the associated Protobuf object.
     * @return a Protobuf object equivalent to the caller object.
     */
    fun toProto(): QueryPb.ConstraintExpr.Constraint = QueryPb.ConstraintExpr.Constraint.newBuilder()
        .also {
            it.attributeName = attributeName
            constraint.extendProto(it)
        }.build()

    companion object {
        /**
         * From the Constraint Protobuf object to the associated instance of Constraint.
         *
         * @param pb the Protobuf object that represents the Constraint object.
         * @return an instance of Constraint equivalent to the Protobuf object provided in input.
         */
        fun fromProto(pb: QueryPb.ConstraintExpr.Constraint): Constraint = Constraint(
            pb.attributeName,
            ConstraintType.fromProto(pb)
        )
    }
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

    companion object {
        /**
         * From the Query Protobuf object to the associated instance of [Query].
         *
         * @param obj the Protobuf object that represents the [Query] object.
         * @return an instance of [Query] equivalent to the Protobuf object provided in input.
         */
        fun fromProto(obj: QueryPb.Model): Query = Query(
            obj.constraintsList.map { ConstraintExpr.fromProto(it) },
            if (obj.hasModel())  DataModel().apply{fromProto(obj.model)} else null
        )
    }

    /**
     *   Return the associated Protobuf object.
     *
     *   @return a Protobuf object equivalent to the caller object.
     */
    fun toProto(): QueryPb.Model = QueryPb.Model.newBuilder()
        .also {
            model?.let { m -> it.setModel(m.toProto()) }
            constraints.forEach { c ->
                it.addConstraints(c.toProtoConstraintExpr())
            }
        }
        .build()
}
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
package fetch.oef.sdk.kotlin

import fetch.oef.pb.QueryOuterClass
import fetch.oef.pb.QueryOuterClass.Query as QueryPb


sealed class ConstraintExpr {

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

class And(val constraints: List<ConstraintExpr>) : ConstraintExpr() {
    fun toProto(): QueryPb.ConstraintExpr.And =  QueryPb.ConstraintExpr.And.newBuilder()
        .also {
            constraints.forEach { constraint->
                it.addExpr(constraint.toProtoConstraintExpr())
            }
        }
        .build()

    companion object {
        fun fromProto(obj: QueryPb.ConstraintExpr.And): And = And(obj.exprList.map { ConstraintExpr.fromProto(it) })
    }
}

class Or(val constraints: List<ConstraintExpr>)  : ConstraintExpr() {
    fun toProto(): QueryPb.ConstraintExpr.Or = QueryPb.ConstraintExpr.Or.newBuilder()
        .also {
            constraints.forEach { constraint->
                it.addExpr(constraint.toProtoConstraintExpr())
            }
        }
        .build()

    companion object {
        fun fromProto(obj: QueryPb.ConstraintExpr.Or): Or = Or(obj.exprList.map { ConstraintExpr.fromProto(it) })
    }
}

class Not(val constraint: ConstraintExpr) : ConstraintExpr() {
    fun toProto(): QueryPb.ConstraintExpr.Not = QueryPb.ConstraintExpr.Not.newBuilder()
        .also {
            it.expr = constraint.toProtoConstraintExpr()
        }
        .build()

    companion object {
        fun fromProto(pb: QueryPb.ConstraintExpr.Not) = Not(ConstraintExpr.fromProto(pb.expr))
    }
}

sealed class ConstraintType {
    fun extendProto(pb:  QueryPb.ConstraintExpr.Constraint.Builder) = pb.also {
        when (this) {
            is Range -> it.range = toProto()
            is Relation -> it.relation = toProto()
            is Set -> it.set = toProto()
            is Distance -> it.distance = toProto()
        }
    }

    companion object {
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

sealed class Range : ConstraintType() {
    data class StringPair  (val first: String,   val second: String)   : Range()
    data class IntPair     (val first: Long,     val second: Long  )   : Range()
    data class DoublePair  (val first: Double,   val second: Double)   : Range()
    data class LocationPair(val first: Location, val second: Location) : Range()

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
        fun fromProto(obj: QueryPb.Range) = when {
            obj.hasS() -> StringPair(obj.s.first, obj.s.second)
            obj.hasI() -> IntPair   (obj.i.first, obj.i.second)
            obj.hasD() -> DoublePair(obj.d.first, obj.d.second)
            obj.hasL() -> LocationPair(Location.fromProto(obj.l.first), Location.fromProto(obj.l.second))
            else -> {
                throw UnknownTypeException("Unexpected Range type! ")
            }
        }
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
fun <T> createRange(first: T, second: T): Range = Range.fromKotlinType(first, second)

sealed class Relation (val value: Value) : ConstraintType() {

    protected abstract fun getPbEnumType(): QueryPb.Relation.Operator

    fun toProto(): QueryPb.Relation = QueryPb.Relation.newBuilder()
        .setOp(getPbEnumType())
        .setVal(value.toProto())
        .build()

    companion object {
        fun fromProto(obj: QueryPb.Relation): Relation = when(obj.op){
            QueryPb.Relation.Operator.EQ    -> EQ  (obj.`val`)
            QueryPb.Relation.Operator.LT    -> LT  (obj.`val`)
            QueryPb.Relation.Operator.LTEQ  -> LTEQ(obj.`val`)
            QueryPb.Relation.Operator.GT    -> GTEQ(obj.`val`)
            QueryPb.Relation.Operator.GTEQ  -> GTEQ(obj.`val`)
            QueryPb.Relation.Operator.NOTEQ -> NOTEQ(obj.`val`)
        }
    }

    class EQ<T>(value: T)    : Relation(Value.fromKotlinType(value)) {
        override fun getPbEnumType() = QueryOuterClass.Query.Relation.Operator.EQ
    }
    class LT<T>(value: T)    : Relation(Value.fromKotlinType(value)) {
        override fun getPbEnumType() = QueryOuterClass.Query.Relation.Operator.LT
    }
    class LTEQ<T>(value: T)  : Relation(Value.fromKotlinType(value)) {
        override fun getPbEnumType() = QueryOuterClass.Query.Relation.Operator.LTEQ
    }
    class GT<T>(value: T)    : Relation(Value.fromKotlinType(value)) {
        override fun getPbEnumType() = QueryOuterClass.Query.Relation.Operator.GT
    }
    class GTEQ<T>(value: T)  : Relation(Value.fromKotlinType(value)) {
        override fun getPbEnumType() = QueryOuterClass.Query.Relation.Operator.GTEQ
    }
    class NOTEQ<T>(value: T) : Relation(Value.fromKotlinType(value)) {
        override fun getPbEnumType() = QueryOuterClass.Query.Relation.Operator.NOTEQ
    }
}

sealed class Set (
    protected var values: Values,
    protected val operation: QueryPb.Set.Operator
) : ConstraintType() {
    protected sealed class Values {
        data class Ints     (val values: List<Long>)     : Values()
        data class Doubles  (val values: List<Double>)   : Values()
        data class Strings  (val values: List<String>)   : Values()
        data class Bools    (val values: List<Boolean>)  : Values()
        data class Locations(val values: List<Location>) : Values()

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

    class IN<T>(values: List<T>)    : Set(Values.fromKotlinType(values), QueryPb.Set.Operator.IN)
    class NOTIN<T>(values: List<T>) : Set(Values.fromKotlinType(values), QueryPb.Set.Operator.NOTIN)

    fun toProto(): QueryPb.Set = QueryPb.Set.newBuilder()
        .setOp(operation)
        .setVals(values.toProto())
        .build()

    protected fun changeValues(values: Values) {
        this.values = values
    }

    companion object {
        fun fromProto(obj: QueryPb.Set): Set = when(obj.op) {
            QueryPb.Set.Operator.IN    -> IN(listOf<Int>()).apply    { changeValues(Values.fromProto(obj.vals)) }
            QueryPb.Set.Operator.NOTIN -> NOTIN(listOf<Int>()).apply { changeValues(Values.fromProto(obj.vals)) }
            else -> {
                throw UnknownTypeException("Set operation type not supported!")
            }
        }
    }

}

data class Distance (
    val center: Location,
    val distance: Double
) : ConstraintType() {
    fun toProto(): QueryPb.Distance = QueryPb.Distance.newBuilder().also {
        it.center = center.toProto()
        it.distance = distance
    }.build()

    companion object {
        fun fromProto(pb: QueryPb.Distance) = Distance(Location.fromProto(pb.center), pb.distance)
    }
}


class Constraint (
    val attributeName: String,
    val constraint: ConstraintType
): ConstraintExpr() {
    fun toProto(): QueryPb.ConstraintExpr.Constraint = QueryPb.ConstraintExpr.Constraint.newBuilder()
        .also {
            it.attributeName = attributeName
            constraint.extendProto(it)
        }.build()

    companion object {
        fun fromProto(pb: QueryPb.ConstraintExpr.Constraint): Constraint = Constraint(
            pb.attributeName,
            ConstraintType.fromProto(pb)
        )
    }
}

class Query (
    private val constraints: List<ConstraintExpr>,
    private val model: DataModel? = null
) {

    constructor() : this(listOf<Constraint>())

    companion object {
        fun fromProto(obj: QueryPb.Model): Query = Query(
            obj.constraintsList.map { ConstraintExpr.fromProto(it) },
            if (obj.hasModel())  DataModel().apply{fromProto(obj.model)} else null
        )
    }

    fun toProto(): QueryPb.Model = QueryPb.Model.newBuilder()
        .also {
            model?.let { m -> it.setModel(m.toProto()) }
            constraints.forEach { c ->
                it.addConstraints(c.toProtoConstraintExpr())
            }
        }
        .build()
}
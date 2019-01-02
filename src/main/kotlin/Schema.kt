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

import fetch.oef.pb.AgentOuterClass
import fetch.oef.pb.QueryOuterClass.Query as QueryPb
import java.lang.Exception


/**
 * Type aliases
 */
typealias AttributeType = QueryPb.Attribute.Type


/**
 * Description of a single element of datum of either a description or a service.
 * This defines the schema that a single entry in a schema must take.
 * <pre>
 * {@code
 *      val attr_title    = AttributeSchema("title" ,          str,   True,  "The title of the book.")
 *      val attr_author   = AttributeSchema("author" ,         str,   True,  "The author of the book.")
 *      val attr_genre    = AttributeSchema("genre",           str,   True,  "The genre of the book.")
 *      val attr_year     = AttributeSchema("year",            int,   True,  "The year of publication of the book.")
 *      val attr_avg_rat  = AttributeSchema("average_rating",  float, False, "The average rating of the book.")
 *      val attr_isbn     = AttributeSchema("ISBN",            str,   True,  "The ISBN.")
 *      val attr_ebook    = AttributeSchema("ebook_available", bool,  False, "If the book can be sold as an e-book.")
 * }
 * </pre>
 */
class AttributeSchema(
    name: String,
    type: AttributeType,
    required: Boolean,
    description: String? = null
) : ProtobufSerializable<QueryPb.Attribute> {

    var name: String = name
        private set
    var type: AttributeType = type
        private set
    var required: Boolean = required
        private set
    var description: String?  = description
        private set

    constructor() : this("", AttributeType.INT, true)

    override fun fromProto(obj: QueryPb.Attribute) {
        name        = obj.name
        type        = obj.type
        required    = obj.required
        description = when(obj.hasDescription()){
            true  -> obj.description
            false -> null
        }
    }

    override fun toProto(): QueryPb.Attribute =  QueryPb.Attribute.newBuilder()
        .setName(name)
        .setType(type)
        .setRequired(required)
        .also {builder->
            description?.let{
                builder.setDescription(it)
            }
        }
        .build()

    override fun equals(other: Any?): Boolean {
        if (other?.javaClass != javaClass) return false
        other as AttributeSchema
        if (other.name != name) return false
        if (other.type != type) return false
        if (other.required != required) return false
        if (other.description != description) return false
        return true
    }
}


/**
 *  This class represents a data model (a.k.a. schema) of the OEFCore.
 *
 *  Examples:
 *  <pre>
 *  {@code
 *      val book_model = DataModel("book", listOf
 *          AttributeSchema("title" ,          str,   True,  "The title of the book."),
 *          AttributeSchema("author" ,         str,   True,  "The author of the book."),
 *          AttributeSchema("genre",           str,   True,  "The genre of the book."),
 *          AttributeSchema("year",            int,   True,  "The year of publication of the book."),
 *          AttributeSchema("average_rating",  float, False, "The average rating of the book."),
 *          AttributeSchema("ISBN",            str,   True,  "The ISBN."),
 *          AttributeSchema("ebook_available", bool,  False, "If the book can be sold as an e-book."),
 *          ...), "A data model to describe books.")
 *}
 * </pre>
 */
class DataModel(
    name: String,
    attributes: List<AttributeSchema>,
    private var description: String? = null
) : ProtobufSerializable<QueryPb.DataModel> {

    var attributes: List<AttributeSchema> = attributes
        private set

    var name: String = name
        private set

    constructor() : this("", listOf())

    override fun fromProto(obj: QueryPb.DataModel) {
        name       = obj.name
        attributes = obj.attributesList.map {
            AttributeSchema().apply {
                fromProto(it)
            }
        }
        description = when(obj.hasDescription()){
            true  -> obj.description
            false -> null
        }
    }

    override fun toProto(): QueryPb.DataModel = QueryPb.DataModel.newBuilder()
        .setName(name)
        .also{ builder->
            attributes.forEach {
                builder.addAttributes(it.toProto())
            }
        }
        .also {builder->
            description?.let {
                builder.setDescription(it)
            }
        }
        .build()

    override fun equals(other: Any?): Boolean {
        if (other?.javaClass != javaClass) return false
        other as DataModel
        if (other.name != name) return false
        if (other.description != description) return false
        if (attributes.size != other.attributes.size ) return false
        for (i in 0.until(attributes.size) ){
            if (attributes[i]!=other.attributes[i]) return false
        }
        return true
    }
}

class UnknownTypeException(message: String) : Exception(message)

sealed class Value {
    data class INT   (val value: Long)    : Value()
    data class DOUBLE(val value: Double)  : Value()
    data class BOOL  (val value: Boolean) : Value()
    data class STRING(val value: String)  : Value()

    companion object {
        fun fromProto(obj: QueryPb.Value) = when{
            obj.hasI() -> INT(obj.i)
            obj.hasD() -> DOUBLE(obj.d)
            obj.hasB() -> BOOL(obj.b)
            obj.hasS() -> STRING(obj.s)
            else -> {
                throw UnknownTypeException("Unexpected Value type!")
            }
        }
        internal fun <T> fromKotlinType(value: T) = when(value) {
            is Long          -> INT(value)
            is Int           -> INT(value.toLong())
            is Double        -> DOUBLE(value)
            is Float         -> DOUBLE(value.toDouble())
            is Boolean       -> BOOL(value)
            is String        -> STRING(value)
            is Value         -> value
            is QueryPb.Value -> fromProto(value)
            else -> {
                throw Exception("Unsupported value type! Only long, int, double, bool and string is supported!")
            }
        }
    }

    fun toProto(): QueryPb.Value = QueryPb.Value.newBuilder()
        .also {
            when(this){
                is INT    -> it.i = value
                is DOUBLE -> it.d = value
                is BOOL   -> it.b = value
                is STRING -> it.s = value
            }
        }
        .build()

    internal fun getAttributeType(): AttributeType = when(this){
        is Value.INT    -> AttributeType.INT
        is Value.DOUBLE -> AttributeType.DOUBLE
        is Value.BOOL   -> AttributeType.BOOL
        is Value.STRING -> AttributeType.STRING
    }

    override fun equals(other: Any?): Boolean {
        if (other?.javaClass != javaClass) return false
        other as Value
        if (other.getAttributeType() != getAttributeType()) return false
        when(this){
            is INT -> {
                other as INT
                if (value != other.value) return false
            }
            is DOUBLE -> {
                other as DOUBLE
                if (value != other.value) return false
            }
            is BOOL -> {
                other as BOOL
                if (value != other.value) return false
            }
            is STRING -> {
                other as STRING
                if (value != other.value) return false
            }
        }
        return true
    }
}

class KeyValue (val name: String, val value: Value)  {

    companion object {
        fun fromProto(obj: QueryPb.KeyValue) = KeyValue(obj.key, Value.fromProto(obj.value))
    }

    fun toProto(): QueryPb.KeyValue = QueryPb.KeyValue.newBuilder()
        .setKey(name)
        .setValue(value.toProto())
        .build()

    override fun equals(other: Any?): Boolean {
        if (other?.javaClass != javaClass) return false
        other as KeyValue
        if (other.name!=name) return false
        return other.value == value
    }
}


class AttributeInconsistencyException(
    message: String
) : Exception(message)


fun <T> descriptionPair(name: String, value: T): KeyValue = KeyValue(name,Value.fromKotlinType(value))


/**
 * Description of either a service or an agent so it can be understood by the OEF and other agents.
 * Contains values of the description, and an optional schema for checking format of values.
 * Whenever the description is changed (including when it is create), the attribute values will
 * checked to make sure they do not violate the attribute schema.
 *
 * Examples:
 *
 * <pre>
 * {@code
 *     val It = descriptionOf(
 *                  descriptionPair("title", "It"),
 *                  descriptionPair("author", "Stephen King"),
 *                  descriptionPair("genre",  "horror"),
 *                  descriptionPair("year", 1986),
 *                  descriptionPair("average_rating", 4.5),
 *                  descriptionPair("ISBN", "0-670-81302-8"),
 *                  descriptionPair("ebook_available", true)
 *              )
 * }
 * </pre>
 */
class Description(
    attributeValues: List<KeyValue>,
    dataModel: DataModel? = null,
    private var dataModelName: String = dataModel?.name ?: ""
) : ProtobufSerializable<QueryPb.Instance> {

    var attributeValues: List<KeyValue> = attributeValues
        private set
    var dataModel: DataModel?
        private set

    init {
       this.dataModel = dataModel?.run {
            checkConsistency()
            this
        } ?: run{
            DataModel(dataModelName, attributeValues.map {
                AttributeSchema(it.name, it.value.getAttributeType(), true)
            })
        }
    }

    constructor() : this(listOf())

    private fun checkConsistency() {
        val lookupTable = attributeValues.associateBy({it.name}, {it.value.getAttributeType()})
        dataModel?.run {
            val dataModelNames = mutableSetOf<String>()
            attributes.forEach {
                if (it.required && it.name  !in lookupTable) {
                    throw AttributeInconsistencyException("Missing required attribute!")
                }
                if (lookupTable[it.name]!=it.type){
                    throw AttributeInconsistencyException("Attribute type mismatch: ${it.name}")
                }
                dataModelNames.add(it.name)
            }
            lookupTable.forEach {
                if (it.key !in dataModelNames){
                    throw AttributeInconsistencyException("Has attribute which isn't in the DataModel: ${it.key}")
                }
            }
        }
    }

    override fun fromProto(obj: QueryPb.Instance) {
        dataModel = DataModel().apply {
            fromProto(obj.model)
        }
        dataModelName = obj.model.name
        val list = mutableListOf<KeyValue>()
        obj.valuesList.forEach {
            list.add(KeyValue.fromProto(it))
        }
        attributeValues = list
    }

    override fun toProto(): QueryPb.Instance = QueryPb.Instance.newBuilder()
        .also {
            it.model = dataModel?.toProto()
            attributeValues.forEach { attribute ->
                it.addValues(attribute.toProto())
            }
        }
        .build()

    fun toAgentDescription() = AgentOuterClass.AgentDescription.newBuilder()
        .setDescription(toProto())
        .build()

    override fun equals(other: Any?): Boolean {
        if (other?.javaClass != javaClass) return false
        other as Description
        if (attributeValues.size != other.attributeValues.size) return false
        for (i in 0.until(attributeValues.size)) {
            if (attributeValues[i] != other.attributeValues[i]) return false
        }
        if (dataModel != other.dataModel) return false
        if (dataModelName != other.dataModelName) return false
        return true
    }
}

fun descriptionOf(vararg keyValues: KeyValue) = Description(keyValues.asList())
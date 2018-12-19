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
package fetch.oef.sdk.kotlin.types

import fetch.oef.pb.QueryOuterClass.Query
import java.lang.Exception


/**
 * Type aliases
 */
typealias AttributeType = Query.Attribute.Type

/**
 * ProtobufSerializable interface
 */
internal interface ProtobufSerializable <T> {
    fun toProto() : T
    fun fromProto(obj: T)
}


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
) : ProtobufSerializable<Query.Attribute>{

    var name: String = name
        private set
    var type: AttributeType = type
        private set
    var required: Boolean = required
        private set
    var description: String?  = description
        private set

    constructor() : this("", AttributeType.INT, true)

    override fun fromProto(obj: Query.Attribute) {
        name        = obj.name
        type        = obj.type
        required    = obj.required
        description = when(obj.hasDescription()){
            true  -> obj.description
            false -> null
        }
    }

    override fun toProto(): Query.Attribute =  Query.Attribute.newBuilder()
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
    private var name: String,
    attributes: List<AttributeSchema>,
    private var description: String? = null
) : ProtobufSerializable<Query.DataModel> {

    var attributes: List<AttributeSchema> = attributes
        private set

    constructor() : this("", listOf())

    override fun fromProto(obj: Query.DataModel) {
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

    override fun toProto(): Query.DataModel = Query.DataModel.newBuilder()
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


sealed class DescriptionPair (val name: String)  {

    protected val keyValueBuilder: Query.KeyValue.Builder = Query.KeyValue.newBuilder().setKey(name)

    internal fun getAttributeType(): AttributeType = when(this){
        is INT    -> AttributeType.INT
        is DOUBLE -> AttributeType.DOUBLE
        is BOOL   -> AttributeType.BOOL
        is STRING -> AttributeType.STRING
    }

    internal fun toAttributeSchema() = AttributeSchema(name, getAttributeType(),    true)
    abstract fun toProto(): Query.KeyValue

    class INT   (name: String, val value: Long)    : DescriptionPair(name) {
        override fun toProto(): Query.KeyValue = keyValueBuilder
            .setValue(Query.Value.newBuilder().setI(value).build())
            .build()
    }
    class DOUBLE(name: String, val value: Double) : DescriptionPair(name) {
        override fun toProto(): Query.KeyValue = keyValueBuilder
            .setValue(Query.Value.newBuilder().setD(value))
            .build()
    }
    class BOOL  (name: String, val value: Boolean)   : DescriptionPair(name) {
        override fun toProto(): Query.KeyValue = keyValueBuilder
            .setValue(Query.Value.newBuilder().setB(value))
            .build()
    }
    class STRING(name: String, val value: String) : DescriptionPair(name) {
        override fun toProto(): Query.KeyValue = keyValueBuilder
            .setValue(Query.Value.newBuilder().setS(value))
            .build()
    }

    companion object {
        fun fromProto(obj: Query.KeyValue): DescriptionPair = when {
                obj.value.hasI() -> INT(obj.key, obj.value.i)
                obj.value.hasB() -> BOOL(obj.key, obj.value.b)
                obj.value.hasD() -> DOUBLE(obj.key, obj.value.d)
                obj.value.hasS() -> STRING(obj.key, obj.value.s)
                else -> {
                    throw Exception("Unexpected DescriptionPair type!")
                }
            }
    }

    override fun equals(other: Any?): Boolean {
        if (other?.javaClass != javaClass) return false
        other as DescriptionPair
        if (other.name != name) return false
        if (other.getAttributeType() != getAttributeType()) return false
        when(this){
            is INT    -> {
                other as INT
                if (value != other.value) return false
            }
            is DOUBLE -> {
                other as DOUBLE
                if (value != other.value) return false
            }
            is BOOL   -> {
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


class AttributeInconsistencyException(
    message: String
) : Exception(message)


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
 *     val It = Description(listOf(
 *                  DescriptionPair.STRING("title", "It"),
 *                  DescriptionPair.STRING("author", "Stephen King"),
 *                  DescriptionPair.STRING("genre",  "horror"),
 *                  DescriptionPair.INT   ("year", 1986),
 *                  DescriptionPair.DOUBLE("average_rating", 4.5),
 *                  DescriptionPair.STRING("ISBN", "0-670-81302-8"),
 *                  DescriptionPair.BOOL  ("ebook_available", true)
 *              ))
 * }
 * </pre>
 */
class Description(
    private var attributeValues: List<DescriptionPair>,
    private var dataModel: DataModel? = null,
    private var dataModelName: String = ""
) : ProtobufSerializable<Query.Instance> {

    init {
        dataModel = dataModel?.run {
            checkConsistency()
            this
        } ?: run{
            DataModel(dataModelName, attributeValues.map {
                it.toAttributeSchema()
            })
        }
    }

    constructor() : this(listOf())

    private fun checkConsistency() {
        val lookupTable = attributeValues.associateBy({it.name}, {it.getAttributeType()})
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

    override fun fromProto(obj: Query.Instance) {
        dataModel = DataModel().apply {
            fromProto(obj.model)
        }
        dataModelName = obj.model.name
        val list = mutableListOf<DescriptionPair>()
        obj.valuesList.forEach {
            list.add(DescriptionPair.fromProto(it))
        }
        attributeValues = list
    }

    override fun toProto(): Query.Instance = Query.Instance.newBuilder()
        .also {
            it.model = dataModel?.toProto()
            attributeValues.forEach { attribute ->
                it.addValues(attribute.toProto())
            }
        }
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
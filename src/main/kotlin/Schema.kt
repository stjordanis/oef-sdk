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

import fetch.oef.pb.AgentOuterClass
import fetch.oef.pb.QueryOuterClass.Query as QueryPb
import java.lang.Exception
import java.util.*


/**
 * Type aliases
 */
typealias AttributeType = QueryPb.Attribute.Type


/**
 * Description of a single element of datum of either a description or a service.
 * This defines the schema that a single entry in a schema must take.
 * ```
 *      val attr_title    = AttributeSchema("title" ,          AttributeSchema.Type.STRING, True,  "The title of the book.")
 *      val attr_author   = AttributeSchema("author" ,         AttributeSchema.Type.STRING, True,  "The author of the book.")
 *      val attr_genre    = AttributeSchema("genre",           AttributeSchema.Type.STRING, True,  "The genre of the book.")
 *      val attr_year     = AttributeSchema("year",            AttributeSchema.Type.STRING, True,  "The year of publication of the book.")
 *      val attr_avg_rat  = AttributeSchema("average_rating",  AttributeSchema.Type.DOUBLE, False, "The average rating of the book.")
 *      val attr_isbn     = AttributeSchema("ISBN",            AttributeSchema.Type.STRING, True,  "The ISBN.")
 *      val attr_ebook    = AttributeSchema("ebook_available", AttributeSchema.Type.BOOL,   False, "If the book can be sold as an e-book.")
 * ```
 *
 * @property name the name of this attribute
 * @property type the type of this attribute
 * @property required whether does this attribute have to be included
 * @property description optional description of this attribute
 *
 * @param name the name of this attribute
 * @param type the type of this attribute
 * @param required whether does this attribute have to be included
 * @param description optional description of this attribute
 */
class AttributeSchema(
    name: String,
    type: Type,
    required: Boolean,
    description: String? = null
) : ProtobufSerializable<QueryPb.Attribute> {

    var name: String = name
        private set
    var type: Type = type
        private set
    var required: Boolean = required
        private set
    var description: String?  = description
        private set

    constructor() : this("", Type.INT, true)

    /**
     * Constructs AttributeSchema object from protocol buffer message.
     */
    override fun fromProto(obj: QueryPb.Attribute) {
        name        = obj.name
        type        = Type.fromAttributeType(obj.type)
        required    = obj.required
        description = when(obj.hasDescription()){
            true  -> obj.description
            false -> null
        }
    }

    /**
     * Transforms AttributeSchema object to protocol buffer message.
     */
    override fun toProto(): QueryPb.Attribute =  QueryPb.Attribute.newBuilder()
        .setName(name)
        .setType(type.type)
        .setRequired(required)
        .also {builder->
            description?.let{
                builder.setDescription(it)
            }
        }
        .build()

    /**
     * Checks the equality of two AttributeSchema objects.
     */
    override fun equals(other: Any?): Boolean {
        if (other?.javaClass != javaClass) return false
        other as AttributeSchema
        if (other.name != name) return false
        if (other.type.type != type.type) return false
        if (other.required != required) return false
        if (other.description != description) return false
        return true
    }

    /**
     * Calculates the hash code for the object.
     */
    override fun hashCode(): Int {
        return Objects.hash(name, type.type, required, description)
    }

    /**
     * Enum class represeting the supported attribute types.
     */
    enum class Type (internal val type: AttributeType) {
        DOUBLE(AttributeType.DOUBLE),
        INT(AttributeType.INT),
        BOOL(AttributeType.BOOL),
        STRING(AttributeType.STRING),
        LOCATION(AttributeType.LOCATION);

        companion object {
            @JvmStatic
            fun fromAttributeType(attributeType: AttributeType) = when (attributeType) {
                QueryPb.Attribute.Type.DOUBLE -> DOUBLE
                QueryPb.Attribute.Type.INT -> INT
                QueryPb.Attribute.Type.BOOL -> BOOL
                QueryPb.Attribute.Type.STRING -> STRING
                QueryPb.Attribute.Type.LOCATION -> LOCATION
            }
        }
    }
}


/**
 *  This class represents a data model (a.k.a. schema) of the OEFCore.
 *
 *  Examples:
 *  ```
 *      val book_model = DataModel("book", listOf
 *          AttributeSchema("title" ,          AttributeSchema.Type.STRING, True,  "The title of the book."),
 *          AttributeSchema("author" ,         AttributeSchema.Type.STRING, True,  "The author of the book."),
 *          AttributeSchema("genre",           AttributeSchema.Type.STRING, True,  "The genre of the book."),
 *          AttributeSchema("year",            AttributeSchema.Type.INT,    True,  "The year of publication of the book."),
 *          AttributeSchema("average_rating",  AttributeSchema.Type.DOUBLE, False, "The average rating of the book."),
 *          AttributeSchema("ISBN",            AttributeSchema.Type.INT,    True,  "The ISBN."),
 *          AttributeSchema("ebook_available", AttributeSchema.Type.BOOL,   False, "If the book can be sold as an e-book."),
 *          ...), "A data model to describe books.")
 * ```
 *
 * @property name the name of the data model
 * @property attributes the list of attributes that constitutes the data model
 * @property description a short description for the data model
 *
 * @param name the name of the data model
 * @param attributes the list of attributes that constitutes the data model
 * @param description a short description for the data model
 */
class DataModel(
    name: String,
    attributes: List<AttributeSchema>,
    description: String? = null
) : ProtobufSerializable<QueryPb.DataModel> {

    var attributes: List<AttributeSchema> = attributes
        private set

    var name: String = name
        private set

    var description: String = description
        private set

    constructor() : this("", listOf())

    /**
     * Constructs DataModel object from protocol buffer message.
     */
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

    /**
     * Transforms DataModel object to protocol buffer message.
     */
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

    /**
     * Checks the equality of two DataModel objects.
     */
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

    /**
     * Calculates the hash code for the object.
     */
    override fun hashCode(): Int {
        return Objects.hash(name, attributes, description)
    }
}

/**
 * Exception type thworn when we encounter an unexpected type!
 */
class UnknownTypeException(message: String) : Exception(message)

/**
 * Data structure to represent locations (i.e. a pair of latitude and longitude).
 *
 * @property lat the latitude of the location
 * @property lon longitude of the location
 */
data class Location(
    val lat: Double,
    val lon: Double
    ) {
    /**
     * Transforms Location object to protocol buffer message.
     */
    fun toProto(): QueryPb.Location = QueryPb.Location.newBuilder()
        .also {
            it.lat = lat
            it.lon = lon
        }.build()

    companion object {
        fun fromProto(pb: QueryPb.Location) = Location(pb.lon, pb.lat)
    }
}

sealed class Value {
    data class INT   (val value: Long)    : Value()
    data class DOUBLE(val value: Double)  : Value()
    data class BOOL  (val value: Boolean) : Value()
    data class STRING(val value: String)  : Value()
    data class LOCATION(val value: Location) : Value()

    companion object {
        fun fromProto(obj: QueryPb.Value) = when{
            obj.hasI() -> INT(obj.i)
            obj.hasD() -> DOUBLE(obj.d)
            obj.hasB() -> BOOL(obj.b)
            obj.hasS() -> STRING(obj.s)
            obj.hasL() -> LOCATION(Location.fromProto(obj.l))
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
            is Location      -> LOCATION(value)
            is Value         -> value
            is QueryPb.Value -> fromProto(value)
            else -> {
                throw UnknownTypeException("Unsupported value type! Only long, int, double, bool and string is supported!")
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
                is LOCATION -> it.l = value.toProto()
            }
        }
        .build()

    internal fun getAttributeType(): AttributeType = when(this){
        is INT    -> AttributeType.INT
        is DOUBLE -> AttributeType.DOUBLE
        is BOOL   -> AttributeType.BOOL
        is STRING -> AttributeType.STRING
        is LOCATION -> AttributeType.LOCATION
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
            is LOCATION -> {
                other as LOCATION
                if (value != other.value) return false
            }
        }
        return true
    }

    override fun hashCode(): Int {
        when(this){
            is INT -> {
                return value.hashCode()
            }
            is DOUBLE -> {
                return value.hashCode()
            }
            is BOOL -> {
                return value.hashCode()
            }
            is STRING -> {
                return value.hashCode()
            }
            is LOCATION -> {
                return value.hashCode()
            }
        }
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

    override fun hashCode(): Int {
        return Objects.hash(name, value)
    }
}

/**
 * Exception raised when the attribute value list and given data model is inconsistent in [Description].
 */
class AttributeInconsistencyException(
    message: String
) : Exception(message)


/**
 * Creates a [KeyValue] object which can be used in [Description] object creation.
 */
fun <T> descriptionPair(name: String, value: T): KeyValue = KeyValue(name,Value.fromKotlinType(value))


/**

 * Description of either a service or an agent so it can be understood by the OEF and other agents.
 * Contains values of the description, and an optional schema for checking format of values.
 * Whenever the description is changed (including when it is create), the attribute values will
 * checked to make sure they do not violate the attribute schema.
 *
 * Example:
 *
 * ```
 *     val It = descriptionOf(
 *                  descriptionPair("title", "It"),
 *                  descriptionPair("author", "Stephen King"),
 *                  descriptionPair("genre",  "horror"),
 *                  descriptionPair("year", 1986),
 *                  descriptionPair("average_rating", 4.5),
 *                  descriptionPair("ISBN", "0-670-81302-8"),
 *                  descriptionPair("ebook_available", true)
 *              )
 * ```
 *
 * @property attributeValues the values of each attribute in the description. This is a dictionary from attribute
 * name to attribute value, each attribute value must have a type in [AttributeSchema.Type]
 * @property dataModel optional schema of this description. If none is provided then the attribute values will not be
 * checked against a schema. Schemas are extremely useful for preventing problems hard to debug, and are highly recommended.
 * @property dataModelName the name of the default data model. If a data model is provided, this parameter is ignored.
 */
class Description @JvmOverloads constructor(
    attributeValues: List<KeyValue>,
    dataModel: DataModel? = null,
    dataModelName: String = dataModel?.name ?: ""
) : ProtobufSerializable<QueryPb.Instance> {

    var attributeValues: List<KeyValue> = attributeValues
        private set
    var dataModel: DataModel?
        private set
    var dataModelName: String = dataModelName
        private  set

    init {
       this.dataModel = dataModel?.run {
            checkConsistency()
            this
        } ?: run{
            DataModel(dataModelName, attributeValues.map {
                AttributeSchema(it.name, AttributeSchema.Type.fromAttributeType(it.value.getAttributeType()), true)
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
                if (lookupTable[it.name]!=it.type.type){
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

    /**
     * Constructs Description object from protocol buffer message.
     */
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

    /**
     * Transforms Description object to protocol buffer message.
     */
    override fun toProto(): QueryPb.Instance = QueryPb.Instance.newBuilder()
        .also {
            it.model = dataModel?.toProto()
            attributeValues.forEach { attribute ->
                it.addValues(attribute.toProto())
            }
        }
        .build()

    /**
     * Transforms the object to AgentDescription protocol buffer message.
     */
    fun toAgentDescription() = AgentOuterClass.AgentDescription.newBuilder()
        .setDescription(toProto())
        .build()

    /**
     * Checks the equality of two Description objects.
     */
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

    /**
     * Calculates the hash code for the object.
     */
    override fun hashCode(): Int {
        return Objects.hash(attributeValues, dataModel)
    }
}

/**
 * Creates [Description] object form the given [KeyValue] argument list.
 */
fun descriptionOf(vararg keyValues: KeyValue) = Description(keyValues.asList())
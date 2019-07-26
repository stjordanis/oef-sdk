package ai.fetch.oef


internal const val TYPE_STRING = "string"
internal const val TYPE_DATA_MODEL = "data_model"
internal const val TYPE_EMBEDDING = "embedding"
internal const val TYPE_INT32 = "int32"
internal const val TYPE_INT64 = "int64"
internal const val TYPE_FLOAT = "float"
internal const val TYPE_DOUBLE = "double"
internal const val TYPE_BOOL = "bool"
internal const val TYPE_LOCATION = "location"
internal const val TYPE_ADDRESS = "address"
internal const val TYPE_KEYVALUE = "keyvalue"

internal const val OPERATOR_EQ = "=="
internal const val OPERATOR_NE = "!="
internal const val OPERATOR_LE = "<="
internal const val OPERATOR_GE = ">="
internal const val OPERATOR_LT = "<"
internal const val OPERATOR_GT = ">"
internal const val OPERATOR_CLOSE_TO = "CLOSE_TO"
internal const val OPERATOR_IN = "IN"
internal const val OPERATOR_NOT_IN = "NOTIN"

internal const val COMBINER_ALL = "all"
internal const val COMBINER_ANY = "any"
internal const val COMBINER_NONE = "none"

internal enum class ValueMessageType(val code: String) {
    BOOL("bool"),
    STRING("string"),
    FLOAT("float"),
    DOUBLE("double"),
    INT32("int32"),
    INT64("int64"),

    DATA_MODEL("data_model"),
    EMBEDDING("embedding"),

    STRING_PAIR_LIST("string_pair_list"),

    LOCATION("location");

    fun toListTypeCode() = code + "_list"
    fun toRangeTypeCode() = code + "_range"
}
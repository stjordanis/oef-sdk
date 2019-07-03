from protocol.src.python.Wrappers import Location


TYPE_STRING = "string"
TYPE_DATA_MODEL = "dm"
TYPE_EMBEDDING = "embedding"
TYPE_INT32 = "int32"
TYPE_INT64 = "int64"
TYPE_FLOAT = "float"
TYPE_DOUBLE = "double"
TYPE_BOOL = "bool"
TYPE_LOCATION = "location"
TYPE_ADDRESS = "address"
TYPE_KEYVALUE = "keyvalue"

OPERATOR_EQ = "=="
OPERATOR_NE = "!="
OPERATOR_LE = "<="
OPERATOR_GE = ">="
OPERATOR_LT = "<"
OPERATOR_GT = ">"
OPERATOR_CLOSE_TO = "CLOSE_TO"
OPERATOR_IN = "IN"
OPERATOR_NOT_IN = "NOTIN"

COMBINER_ALL = "all"
COMBINER_ANY = "any"
COMBINER_NONE = "none"


def listOf(x):
    return("{}_list".format(x))


def rangeOf(x):
    return("{}_range".format(x))


def pythonTypeToString(value):
    if isinstance(value, int):
        return TYPE_INT64
    elif isinstance(value, float):
        return TYPE_DOUBLE
    elif isinstance(value, str):
        return TYPE_STRING
    elif isinstance(value, bool):
        return TYPE_BOOL
    elif isinstance(value, Location):
        return TYPE_LOCATION
    else:
        print("pythonTypeToString, type not supported: ", type(value), ". value=", value)


def populateUpdateTFV(tfv, fieldname, data, typename=None):
    tfv.fieldname = fieldname
    if typename == 'location':
        tfv.value.l.lat = data[0]
        tfv.value.l.lon = data[1]
        tfv.value.type = 9
        return

    if isinstance(data, str):
        tfv.value.type = 2
        tfv.value.s = data
        return
    if isinstance(data, int):
        tfv.value.type = 3
        tfv.value.i = data
        return
    if isinstance(data, float):
        tfv.value.type = 5
        tfv.value.d = data
        return
    raise ValueError("TFV type bad")


def decodeAttributeValueToInfo(av):
    return {
        0: ( None, lambda x: None),
        1: ( None, lambda x: None),
        2: ( TYPE_STRING,lambda x: x.s),
        3: ( TYPE_INT64,lambda x: x.i),
        4: ( TYPE_FLOAT,lambda x: x.f),
        5: ( TYPE_DOUBLE,lambda x: x.d),
        6: ( TYPE_DATA_MODEL, lambda x: x.dm), # not impl yet
        7: ( TYPE_INT32,lambda x: x.i32),
        8: ( TYPE_BOOL,lambda x: x.b),
        9: ( TYPE_LOCATION,lambda x: x.l),
        10: (TYPE_ADDRESS, lambda x: x.a),
        11: (TYPE_KEYVALUE, lambda x: x.kv)
    }.get(av.type, ( None, lambda x: None))


def decodeKeyValuesToKVTs(kv_list):
    r = []
    for kv in kv_list:
        result_value = None
        key = kv.key
        value = kv.value

        if value.HasField("s"):
            r.append(( key, "string", value.s))
        elif value.HasField("d"):
            r.append(( key, "float",  value.d))
        elif value.HasField("b"):
            r.append(( key, "bool",   value.b))
        elif value.HasField("i"):
            r.append(( key, "int",    value.i))
        elif value.HasField("l"):
            r.append(( key, "location", ( value.l.lat, value.l.lon )))
        else:
            r.append(( key, None, None ))
    return r


# Produce a value which can be fed into the operator factory system.
def decodeAttributeValueInfoToPythonType(av):
    t, data = decodeAttributeValueToTypeValue(av)
    type_string, converter_function = {
        TYPE_STRING     : ("string",   lambda x: x),
        TYPE_INT64      : ("int",      lambda x: x),
        TYPE_FLOAT      : ("double",   lambda x: x),
        TYPE_DOUBLE     : ("double",   lambda x: x),
        #TYPE_DATA_MODEL : (None, lambda x: None), # not impl yet
        TYPE_INT32      : ("int",      lambda x: x),
        TYPE_BOOL       : ("bool",     lambda x: x),
        TYPE_LOCATION   : ("location", lambda x: (x.lat, x.lon)),
        TYPE_KEYVALUE   : ("key-type-value_list", lambda x: decodeKeyValuesToKVTs(x))
    }.get(t, ( None, lambda x: None))
    return type_string, converter_function(data)


def decodeAttributeValueToTypeValue(av):
    t, func = decodeAttributeValueToInfo(av)
    v = func(av)
    return t, v


def decodeAttributeValueToType(av):
    return decodeAttributeValueToInfo(av)[0]


def typeToRange(type_str: str):
    return type_str + "_range"

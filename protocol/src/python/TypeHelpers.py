from protocol.src.proto import dap_interface_pb2


def _set_location(target, data):
    """
    Updates location part of a ValueMessage protobuf.

    :param target: ValueMessage protobuf
    :param data: source data, format: data[0]=coordinate_system, data[1]=unit, data[2]=list of doubles
    :return:
    """
    if type(target.l) == type(data):
        target.l.CopyFrom(data)
        return
    target.l.coordinate_system = data[0]
    target.l.unit = data[1]
    target.l.v.append(data[2][0])
    target.l.v.append(data[2][1])


def _get_location(src):
    return src.coordinate_system, src.unit, src.v[:],


def encodeConstraintValue(data, typecode, logger):
    valueMessage = dap_interface_pb2.ValueMessage()
    valueMessage.typecode = typecode

    if typecode == 'string':
        valueMessage.s = data
    elif typecode == 'bool':
        valueMessage.b = data
    elif typecode == 'float':
        valueMessage.f = data
    elif typecode == 'double':
        valueMessage.d = data
    elif typecode == 'int32':
        valueMessage.i32 = data
    elif typecode == 'int' or typecode == 'int64':
        valueMessage.typecode = 'int64'
        valueMessage.i64 = data

    elif typecode == 'location':
        _set_location(valueMessage, data)

    elif typecode == 'data_model':
        valueMessage.dm.CopyFrom(data)

    elif typecode == 'string_list':
        valueMessage.v_s.extend(data)
    elif typecode == 'float_list':
        valueMessage.v_f.extend(data)
    elif typecode == 'double_list':
        valueMessage.v_d.extend(data)
    elif typecode == 'i32_list':
        valueMessage.v_i32.extend(data)
    elif typecode == 'i64_list':
        valueMessage.v_i64.extend(data)

    elif typecode == 'location_list':
        for d in data:
            _set_location(valueMessage.v_l.add(), d)

    elif typecode == 'string_pair':
        valueMessage.d.append(data[0])
        valueMessage.d.append(data[1])

    elif typecode == 'string_pair_list':
        for d in data:
            valueMessage.d.append(d[0])
            valueMessage.d.append(d[1])

    elif typecode == 'string_range':
        valueMessage.v_s.append(data[0])
        valueMessage.v_s.append(data[1])
    elif typecode == 'float_range':
        valueMessage.v_f.append(data[0])
        valueMessage.v_f.append(data[1])
    elif typecode == 'double_range':
        valueMessage.v_d.append(data[0])
        valueMessage.v_d.append(data[1])
    elif typecode == 'i32_range':
        valueMessage.v_i32.append(data[0])
        valueMessage.v_i32.append(data[1])
    elif typecode == 'i64_range':
        valueMessage.v_i64.append(data[0])
        valueMessage.v_i64.append(data[1])

    elif typecode == 'location_range':
        _set_location(valueMessage.v_l.add(), data[0])
        _set_location(valueMessage.v_l.add(), data[1])

    else:
        logger.error("encodeConstraintValue doesn't know how to write a '{}'".format(typecode))

    return valueMessage


def decodeConstraintValue(valueMessage):
    return {
        'bool':          lambda x: x.b,
        'string':        lambda x: x.s,
        'float':         lambda x: x.f,
        'double':        lambda x: x.d,
        'int32':         lambda x: x.i32,
        'int64':         lambda x: x.i64,

        'bool_list':     lambda x: x.b_s,
        'string_list':   lambda x: x.v_s,
        'float_list':    lambda x: x.v_f,
        'double_list':   lambda x: x.v_d,
        'int32_list':    lambda x: x.v_i32,
        'int64_list':    lambda x: x.v_i64,

        'data_model':    lambda x: x.dm,
        'embedding':     lambda x: x.v_d,

        'string_pair':      lambda x: (x.v_s[0], x.v_s[1],),
        'string_pair_list': lambda x: [ ( x.v_d[i], x.v_d[i+1], ) for i in range(0, len(x.v_d), 2) ],

        'string_range':  lambda x: (x.v_s[0], x.v_s[1],),
        'float_range':   lambda x: (x.v_f[0], x.v_f[1],),
        'double_range':  lambda x: (x.v_d[0], x.v_d[1],),
        'int32_range':   lambda x: (x.v_i32[0], x.v_i32[1],),
        'int64_range':   lambda x: (x.v_i64[0], x.v_i64[1],),

        'location':       lambda x: _get_location(x.l),
        'location_range': lambda x: (_get_location(x.v_l[0]), _get_location(x.v_l[1])),
        'location_list':  lambda x: [_get_location(y) for y in x.v_l],

    }[valueMessage.typecode](valueMessage)

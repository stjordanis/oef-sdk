# -*- coding: utf-8 -*-

# ------------------------------------------------------------------------------
#
#   Copyright 2018 Fetch.AI Limited
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.
#
# ------------------------------------------------------------------------------


"""

oef.schema
~~~~~~~~~~

This module defines classes to deal with data models and their instances.

"""


import copy
from abc import ABC, abstractmethod
from typing import Union, Type, Optional, List, Dict

import oef.agent_pb2 as agent_pb2
from protocol.src.proto import dap_interface_pb2
from utils.src.python.Logging import has_logger
from protocol.src.python.Interfaces import ProtobufSerializable
from protocol.src.python.Wrappers import Location

"""
The allowable types that an Attribute can have
"""
ATTRIBUTE_TYPES = Union[float, str, bool, int, Location]


class AttributeSchema(ProtobufSerializable):
    """
    Description of a single element of datum of either a description or a service.

    This defines the schema that a single entry in a schema must take.

    Examples:
        >>> attr_title    = AttributeSchema("title" ,          str,   True,  "The title of the book.")
        >>> attr_author   = AttributeSchema("author" ,         str,   True,  "The author of the book.")
        >>> attr_genre    = AttributeSchema("genre",           str,   True,  "The genre of the book.")
        >>> attr_year     = AttributeSchema("year",            int,   True,  "The year of publication of the book.")
        >>> attr_avg_rat  = AttributeSchema("average_rating",  float, False, "The average rating of the book.")
        >>> attr_isbn     = AttributeSchema("ISBN",            str,   True,  "The ISBN.")
        >>> attr_ebook    = AttributeSchema("ebook_available", bool,  False, "If the book can be sold as an e-book.")

    """

    """mapping from attribute types to its associated pb"""
    _attribute_type_to_pb = {
        bool: dap_interface_pb2.ValueMessage.Attribute.BOOL,
        int: dap_interface_pb2.ValueMessage.Attribute.INT,
        float: dap_interface_pb2.ValueMessage.Attribute.DOUBLE,
        str: dap_interface_pb2.ValueMessage.Attribute.STRING,
        Location: dap_interface_pb2.ValueMessage.Attribute.LOCATION
    }

    def __init__(self,
                 attribute_name: str,
                 attribute_type: Type[ATTRIBUTE_TYPES],
                 is_attribute_required: bool,
                 attribute_description: Optional[str] = None) -> None:
        """
        Initialize an attribute schema.

        :param attribute_name: the name of this attribute.
        :param attribute_type: the type of this attribute, must be a type in ATTRIBUTE_TYPES.
        :param is_attribute_required: whether does this attribute have to be included.
        :param attribute_description: optional description of this attribute.
        """
        self.name = attribute_name
        self.type = attribute_type
        self.required = is_attribute_required
        self.description = attribute_description

    def to_pb(self) -> dap_interface_pb2.ValueMessage.Attribute:
        """
        Convert the attribute into a Protobuf object

        :return: the associated Attribute protobuf object.
        """
        attribute = dap_interface_pb2.ValueMessage.Attribute()
        attribute.name = self.name
        attribute.type = self._attribute_type_to_pb[self.type]
        attribute.required = self.required
        if self.description is not None:
            attribute.description = self.description
        return attribute

    @classmethod
    def from_pb(cls, attribute: dap_interface_pb2.ValueMessage.Attribute):
        """
        Unpack the attribute Protobuf object.

        :param attribute: the Protobuf object associated with the attribute.
        :return: the attribute.
        """
        return cls(attribute.name,
                   dict(map(reversed, cls._attribute_type_to_pb.items()))[attribute.type],
                   attribute.required,
                   attribute.description if attribute.description else None)

    def __eq__(self, other):
        if type(other) != AttributeSchema:
            return False
        else:
            return self.name == other.name and self.type == other.type and self.required == other.required


class AttributeInconsistencyException(Exception):
    """
    Raised when the attributes in a Description are inconsistent.

    Inconsistency is defined when values do not meet their respective schema, or if the values
    are not of an allowed type.
    """
    pass


class DataModel(ProtobufSerializable):
    """
    This class represents a data model (a.k.a. schema) of the OEFCore.

    Examples:
        >>> book_model = DataModel("book", [
        ...  AttributeSchema("title" ,          str,   True,  "The title of the book."),
        ...  AttributeSchema("author" ,         str,   True,  "The author of the book."),
        ...  AttributeSchema("genre",           str,   True,  "The genre of the book."),
        ...  AttributeSchema("year",            int,   True,  "The year of publication of the book."),
        ...  AttributeSchema("average_rating",  float, False, "The average rating of the book."),
        ...  AttributeSchema("ISBN",            str,   True,  "The ISBN."),
        ...  AttributeSchema("ebook_available", bool,  False, "If the book can be sold as an e-book."),
        ... ], "A data model to describe books.")
    """

    def __init__(self,
                 name: str,
                 attribute_schemas: List[AttributeSchema],
                 description: Optional[str] = None) -> None:
        """
        Initialize a Data Model object.

        :param name: the name of the data model.
        :param attribute_schemas: the list of attributes that constitutes the data model.
        :param description: a short description for the data model.
        """
        self.name = name
        self.attribute_schemas = sorted(copy.deepcopy(attribute_schemas), key=lambda x: x.name)
        self.description = description
        self.attributes_by_name = {a.name: a for a in self.attribute_schemas}
        self._check_validity()

    @classmethod
    def from_pb(cls, model: dap_interface_pb2.ValueMessage.DataModel):
        """
        Unpack the data model Protobuf object.

        :param model: the Protobuf object associated with the data model.
        :return: the data model.
        """

        name = model.name
        attributes = [AttributeSchema.from_pb(attr_pb) for attr_pb in model.attributes]
        description = model.description
        return cls(name, attributes, description)

    def to_pb(self):
        """
        Convert the data model into a Protobuf object

        :return: the associated DataModel Protobuf object.
        """
        model = dap_interface_pb2.ValueMessage.DataModel()
        model.name = self.name
        model.attributes.extend([attr.to_pb() for attr in self.attribute_schemas])
        if self.description is not None:
            model.description = self.description
        return model

    def _check_validity(self):
        # check if there are duplicated attribute names
        attribute_names = [attribute.name for attribute in self.attribute_schemas]
        if len(attribute_names) != len(set(attribute_names)):
            raise ValueError("Invalid input value for type '{}': duplicated attribute name."
                             .format(type(self).__name__))

    def __eq__(self, other):
        if type(other) != DataModel:
            return False
        else:
            return self.name == other.name and self.attribute_schemas == other.attribute_schemas


def generate_schema(model_name: str, attribute_values: Dict[str, ATTRIBUTE_TYPES]) -> DataModel:
    """
    Generate a schema that matches the values stored in this description.
    That is, for each attribute (name, value), generate an AttributeSchema.
    It is assumed that each attribute is required.

    :param model_name: the name of the model.
    :param attribute_values: the values of each attribute
    :return: the schema compliant with the values specified.
    """

    return DataModel(model_name, [AttributeSchema(k, type(v), True) for k, v in attribute_values.items()])


class Description(ProtobufSerializable):
    pass

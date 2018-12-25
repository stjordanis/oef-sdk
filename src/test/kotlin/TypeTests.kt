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

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import assertk.assert
import assertk.assertAll
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import fetch.oef.pb.QueryOuterClass.Query

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TypeTestSchema {

    @Test
    fun `Description object creation`() {
      val It = Description(
          listOf(
              descriptionPair("title", "It"),
              descriptionPair("author", "Stephen King"),
              descriptionPair("genre", "horror"),
              descriptionPair("year", 1986),
              descriptionPair("average_rating", 4.5),
              descriptionPair("ISBN", "0-670-81302-8"),
              descriptionPair("ebook_available", true)
          )
      )
        val proto = It.toProto()
        assertAll {
            assert(proto.getValues(0)).isNotNull{
                assertAll {
                    assert(it.actual.key).isEqualTo( "title")
                    assert(it.actual.value.s).isNotNull {v-> v.isEqualTo("It") }
                }
            }
            assert(proto.getValues(4)).isNotNull{
                assertAll {
                    assert(it.actual.key).isEqualTo("average_rating")
                    assert(it.actual.value.d).isNotNull{v-> v.isEqualTo(4.5) }
                }
            }
            assert(proto.model.getAttributes(0)).isNotNull{
                assertAll {
                    assert(it.actual.name).isEqualTo("title")
                    assert(it.actual.type).isEqualTo(Query.Attribute.Type.STRING)
                }
            }
            assert(proto.model.getAttributes(3)).isNotNull{
                assertAll {
                    assert(it.actual.name).isEqualTo("year")
                    assert(it.actual.type).isEqualTo(Query.Attribute.Type.INT)
                }
            }
        }
    }

    @Test
    fun `Description object proto transformation`() {
        val It = Description(
            listOf(
                descriptionPair("title", "It"),
                descriptionPair("author", "Stephen King"),
                descriptionPair("genre", "horror"),
                descriptionPair("year", 1986),
                descriptionPair("average_rating", 4.5),
                descriptionPair("ISBN", "0-670-81302-8"),
                descriptionPair("ebook_available", true)
            )
        )
        val proto = It.toProto()
        val It2 = Description().apply {
            fromProto(proto)
        }

        assertAll {
            assert(It2).isEqualTo(It)
        }
    }
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TypeTestQuery {

    @Test
    fun `Create query object`() {
    }
}
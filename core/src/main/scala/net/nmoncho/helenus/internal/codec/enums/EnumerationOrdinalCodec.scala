/*
 * Copyright (c) 2021 the original author or authors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package net.nmoncho.helenus.internal.codec.enums

import com.datastax.oss.driver.api.core.`type`.codec.MappingCodec
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodecs
import com.datastax.oss.driver.api.core.`type`.reflect.GenericType

/** [[com.datastax.oss.driver.api.core.`type`.codec.TypeCodec]] for an [[Enumeration]] mapped to its Int representation.
  * This class won't be made available as implicit, since there is another possible representation for enums
  *
  * @param enumeration enumeration to map to an int
  */
class EnumerationOrdinalCodec[T <: Enumeration](enumeration: T)
    extends MappingCodec[java.lang.Integer, T#Value](
      TypeCodecs.INT,
      GenericType.of(classOf[T#Value])
    ) {

  override def innerToOuter(value: java.lang.Integer): T#Value =
    if (value == null) null else enumeration(value)

  override def outerToInner(value: T#Value): java.lang.Integer =
    if (value == null) null else value.id

  override def toString: String = s"EnumerationOrdinalCodec[${enumeration.toString}]"
}

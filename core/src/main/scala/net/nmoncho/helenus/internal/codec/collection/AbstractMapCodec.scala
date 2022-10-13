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

package net.nmoncho.helenus.internal.codec
package collection

import com.datastax.oss.driver.api.core.ProtocolVersion
import com.datastax.oss.driver.api.core.`type`.DataType
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.internal.core.`type`.DefaultMapType
import com.datastax.oss.driver.internal.core.`type`.codec.ParseUtils

import java.nio.ByteBuffer
import scala.collection.compat._
import scala.collection.mutable

abstract class AbstractMapCodec[K, V, M[K, V] <: Map[K, V]](
    keyInner: TypeCodec[K],
    valueInner: TypeCodec[V],
    frozen: Boolean
)(implicit factory: Factory[(K, V), M[K, V]])
    extends TypeCodec[M[K, V]] {

  private val openingChar: Char       = '{'
  private val closingChar: Char       = '}'
  private val entrySeparator: Char    = ','
  private val keyValueSeparator: Char = ':'

  override val getCqlType: DataType =
    new DefaultMapType(keyInner.getCqlType, valueInner.getCqlType, frozen)

  override def encode(value: M[K, V], protocolVersion: ProtocolVersion): ByteBuffer =
    if (value == null) null
    else {
      var size    = 4
      val buffers = mutable.ListBuffer[ByteBuffer]()
      for ((k, v) <- value) {
        if (k == null) {
          throw new IllegalArgumentException("Map keys cannot be null")
        }
        if (v == null) {
          throw new IllegalArgumentException("Map values cannot be null")
        }

        val encodedKey   = keyInner.encode(k, protocolVersion)
        val encodedValue = valueInner.encode(v, protocolVersion)

        if (encodedKey == null) {
          throw new NullPointerException("Map keys cannot encode to CQL NULL")
        } else if (encodedValue == null) {
          throw new NullPointerException("Map values cannot encode to CQL NULL")
        }

        size += (4 + encodedKey.remaining()) + (4 + encodedValue.remaining())
        buffers.append(encodedKey)
        buffers.append(encodedValue)
      }

      val result = ByteBuffer.allocate(size)
      result.putInt(value.size)
      for (element <- buffers) {
        result.putInt(element.remaining())
        result.put(element)
      }
      result.flip()

      result
    }

  override def decode(bytes: ByteBuffer, protocolVersion: ProtocolVersion): M[K, V] = {
    val builder = factory.newBuilder

    if (bytes == null || bytes.remaining == 0) builder.result()
    else {
      val input = bytes.duplicate()
      val size  = input.getInt()
      for (_ <- 0 until size) {
        // Allow null elements on the decode path, because Cassandra might return such collections
        // for some computed values in the future -- e.g. SELECT ttl(some_collection)

        // Decode Key
        val keySize = input.getInt()
        val key =
          if (keySize < 0) null.asInstanceOf[K]
          else {
            val copy = input.duplicate()
            copy.limit(copy.position() + keySize)
            input.position(input.position() + keySize)
            keyInner.decode(copy, protocolVersion)
          }

        // Decode Value
        val valueSize = input.getInt()
        val value =
          if (valueSize < 0) null.asInstanceOf[V]
          else {
            val copy = input.duplicate()
            copy.limit(copy.position() + valueSize)
            input.position(input.position() + valueSize)

            valueInner.decode(copy, protocolVersion)
          }

        builder += key -> value
      }

      builder.result()
    }
  }

  override def format(map: M[K, V]): String =
    if (map == null) {
      NULL
    } else {
      val sb   = new mutable.StringBuilder().append(openingChar)
      var tail = false
      for ((key, value) <- map) {
        if (tail) sb.append(entrySeparator)
        else tail = true

        sb.append(keyInner.format(key))
          .append(keyValueSeparator)
          .append(valueInner.format(value))
      }
      sb.append(closingChar).toString()
    }

  override def parse(value: String): M[K, V] =
    if (value == null || value.isEmpty || value.equalsIgnoreCase(NULL)) {
      null.asInstanceOf[M[K, V]]
    } else {
      val builder = factory.newBuilder
      var idx     = skipSpacesAndExpect(value, 0, openingChar)

      if (value.charAt(idx) == closingChar) {
        builder.result()
      } else {
        while (idx < value.length) {
          // Parse Key
          val (k, nk) = parseWithCodec(value, keyInner, idx)

          idx = skipSpacesAndExpect(value, nk, keyValueSeparator)

          // Parse Value
          val (v, nv) = parseWithCodec(value, valueInner, idx)

          builder += k -> v

          idx = ParseUtils.skipSpaces(value, nv)
          if (isParseFinished(value, idx, closingChar, entrySeparator)) {
            return builder.result()
          }
          idx = ParseUtils.skipSpaces(value, idx + 1)
        }

        throw new IllegalArgumentException(
          s"Malformed map value '$value', missing closing '$closingChar'"
        )
      }
    }

  override def accepts(value: Any): Boolean = value match {
    case m: Map[_, _] =>
      m.headOption.exists { case (key, value) =>
        keyInner.accepts(key) && valueInner.accepts(value)
      }

    case _ => false
  }
}

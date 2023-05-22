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

package net.nmoncho.helenus.internal.codec.collection

import scala.collection.{ mutable => mutablecoll }

import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.`type`.reflect.GenericType
import com.datastax.oss.driver.api.core.`type`.reflect.GenericTypeParameter
import com.datastax.oss.driver.shaded.guava.common.reflect.TypeToken

/** Defines the mutable collection codec implementations */
package object mutable {

  class BufferCodec[T](inner: TypeCodec[T], frozen: Boolean)
      extends AbstractSeqCodec[T, mutablecoll.Buffer](inner, frozen) {

    override val getJavaType: GenericType[mutablecoll.Buffer[T]] =
      GenericType
        .of(new TypeToken[mutablecoll.Buffer[T]]() {}.getType)
        .where(new GenericTypeParameter[T] {}, inner.getJavaType.wrap())
        .asInstanceOf[GenericType[mutablecoll.Buffer[T]]]

    override def toString: String = s"ArrayBufferCodec[${inner.getCqlType.toString}]"
  }

  object BufferCodec {
    def apply[T](inner: TypeCodec[T], frozen: Boolean): BufferCodec[T] =
      new BufferCodec(inner, frozen)

    def frozen[T](inner: TypeCodec[T]): BufferCodec[T] =
      BufferCodec[T](inner, frozen = true)
  }

  class IndexedSeqCodec[T](inner: TypeCodec[T], frozen: Boolean)
      extends AbstractSeqCodec[T, mutablecoll.IndexedSeq](inner, frozen) {

    override val getJavaType: GenericType[mutablecoll.IndexedSeq[T]] =
      GenericType
        .of(new TypeToken[mutablecoll.IndexedSeq[T]]() {}.getType)
        .where(new GenericTypeParameter[T] {}, inner.getJavaType.wrap())
        .asInstanceOf[GenericType[mutablecoll.IndexedSeq[T]]]

    override def toString: String = s"IndexedSeqCodec[${inner.getCqlType.toString}]"
  }

  object IndexedSeqCodec {
    def apply[T](inner: TypeCodec[T], frozen: Boolean): IndexedSeqCodec[T] =
      new IndexedSeqCodec(inner, frozen)

    def frozen[T](inner: TypeCodec[T]): IndexedSeqCodec[T] =
      IndexedSeqCodec[T](inner, frozen = true)
  }

  class SetCodec[T](inner: TypeCodec[T], frozen: Boolean)
      extends AbstractSetCodec[T, mutablecoll.Set](inner, frozen) {

    override val getJavaType: GenericType[mutablecoll.Set[T]] =
      GenericType
        .of(new TypeToken[mutablecoll.Set[T]]() {}.getType)
        .where(new GenericTypeParameter[T] {}, inner.getJavaType.wrap())
        .asInstanceOf[GenericType[mutablecoll.Set[T]]]

    override def toString: String = s"MutableSetCodec[${inner.getCqlType.toString}]"
  }

  object SetCodec {
    def apply[T](inner: TypeCodec[T], frozen: Boolean): SetCodec[T] =
      new SetCodec(inner, frozen)

    def frozen[T](inner: TypeCodec[T]): SetCodec[T] =
      SetCodec[T](inner, frozen = true)
  }

  class MapCodec[K, V](keyInner: TypeCodec[K], valueInner: TypeCodec[V], frozen: Boolean)
      extends AbstractMapCodec[K, V, mutablecoll.Map](keyInner, valueInner, frozen) {

    override val getJavaType: GenericType[mutablecoll.Map[K, V]] =
      GenericType
        .of(new TypeToken[mutablecoll.Map[K, V]]() {}.getType)
        .where(new GenericTypeParameter[K] {}, keyInner.getJavaType.wrap())
        .where(new GenericTypeParameter[V] {}, valueInner.getJavaType.wrap())
        .asInstanceOf[GenericType[mutablecoll.Map[K, V]]]

    override def toString: String =
      s"MutableMapCodec[${keyInner.getCqlType.toString},${valueInner.getCqlType.toString}]"
  }

  object MapCodec {
    def apply[K, V](
        keyInner: TypeCodec[K],
        valueInner: TypeCodec[V],
        frozen: Boolean
    ): MapCodec[K, V] =
      new MapCodec(keyInner, valueInner, frozen)

    def frozen[K, V](keyInner: TypeCodec[K], valueInner: TypeCodec[V]): MapCodec[K, V] =
      MapCodec[K, V](keyInner, valueInner, frozen = true)
  }

}

/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.flink.typeinfo.collection.mutable

import scala.collection.compat._
import scala.collection.mutable

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.common.typeutils._
import org.apache.flink.core.memory.DataInputView
import org.apache.flink.core.memory.DataOutputView

// $COVERAGE-OFF$
class MapTypeInformation[K, V](
    val key: TypeInformation[K],
    val value: TypeInformation[V]
)(
    implicit factory: Factory[(K, V), mutable.Map[K, V]]
) extends TypeInformation[mutable.Map[K, V]] {

  override def isBasicType: Boolean = false

  override def isTupleType: Boolean = false

  override def getArity: Int = 0

  // similar as arrays, the lists are "opaque" to the direct field addressing logic
  // since the list's elements are not addressable, we do not expose them
  override def getTotalFields: Int = 1

  override def getTypeClass: Class[mutable.Map[K, V]] =
    this.getClass.asInstanceOf[Class[mutable.Map[K, V]]]

  override def isKeyType: Boolean = false

  override def hashCode(): Int = 31 * key.hashCode + value.hashCode()

  override def canEqual(obj: Any): Boolean = obj != null && obj.getClass == obj.getClass

  override def equals(obj: Any): Boolean =
    if (obj == this) {
      true
    } else {
      obj match {
        case other: MapTypeInformation[_, _] =>
          other.getClass == this.getClass && other.key == this.key && other.value == this.value

        case _ =>
          false
      }
    }

  override def createSerializer(config: ExecutionConfig): TypeSerializer[mutable.Map[K, V]] = {
    val keySerializer   = key.createSerializer(config)
    val valueSerializer = value.createSerializer(config)

    new MapTypeInformation.Serializer(keySerializer, valueSerializer, factory)
  }

  override def toString(): String = s"Map[${key.toString}, ${value.toString}]"
}

object MapTypeInformation {

  class Serializer[K, V](
      val keySerializer: TypeSerializer[K],
      val valueSerializer: TypeSerializer[V],
      factory: Factory[(K, V), mutable.Map[K, V]]
  ) extends TypeSerializer[mutable.Map[K, V]] {

    override def isImmutableType: Boolean = false

    override def duplicate(): TypeSerializer[mutable.Map[K, V]] = {
      val duplicateKeySerializer   = keySerializer.duplicate()
      val duplicateValueSerializer = valueSerializer.duplicate()

      if (
        (duplicateKeySerializer eq keySerializer) && (duplicateValueSerializer eq valueSerializer)
      ) this
      else new Serializer(duplicateKeySerializer, duplicateValueSerializer, factory)
    }

    override def createInstance(): mutable.Map[K, V] =
      factory.newBuilder.result()

    override def copy(from: mutable.Map[K, V]): mutable.Map[K, V] =
      from
        .foldLeft(factory.newBuilder) { (iter, value) =>
          iter += value
          iter
        }
        .result()

    override def copy(from: mutable.Map[K, V], reuse: mutable.Map[K, V]): mutable.Map[K, V] =
      copy(from)

    override def getLength: Int = -1 // variable length

    override def serialize(record: mutable.Map[K, V], target: DataOutputView): Unit = {
      target.writeInt(record.size)

      record.foreach { case (key, value) =>
        keySerializer.serialize(key, target)
        valueSerializer.serialize(value, target)
      }
    }

    override def deserialize(source: DataInputView): mutable.Map[K, V] = {
      val size = source.readInt()

      (0 until size)
        .foldLeft(factory.newBuilder) { (builder, _) =>
          builder += keySerializer.deserialize(source) -> valueSerializer.deserialize(source)
        }
        .result()
    }

    override def deserialize(reuse: mutable.Map[K, V], source: DataInputView): mutable.Map[K, V] =
      deserialize(source)

    override def copy(source: DataInputView, target: DataOutputView): Unit = {
      val size = source.readInt

      target.writeInt(size)
      (0 until size).foreach { _ =>
        keySerializer.copy(source, target)
        valueSerializer.copy(source, target)
      }
    }

    override def equals(obj: Any): Boolean = obj match {
      case other: Serializer[_, _] =>
        other.keySerializer == this.keySerializer && other.valueSerializer == this.valueSerializer

      case _ => false
    }

    override def hashCode: Int = 31 * keySerializer.hashCode() + valueSerializer.hashCode()

    override def snapshotConfiguration(): TypeSerializerSnapshot[mutable.Map[K, V]] =
      new CompositeTypeSerializerSnapshot[mutable.Map[K, V], Serializer[K, V]](this.getClass) {

        override def getCurrentOuterSnapshotVersion: Int = 1

        override def getNestedSerializers(
            outerSerializer: Serializer[K, V]
        ): Array[TypeSerializer[_]] =
          Array(outerSerializer.keySerializer, outerSerializer.valueSerializer)

        override def createOuterSerializerWithNestedSerializers(
            nestedSerializers: Array[TypeSerializer[_]]
        ): Serializer[K, V] =
          new Serializer(
            nestedSerializers(0).asInstanceOf[TypeSerializer[K]],
            nestedSerializers(1).asInstanceOf[TypeSerializer[V]],
            factory
          )

      }
  }

}
// $COVERAGE-ON$

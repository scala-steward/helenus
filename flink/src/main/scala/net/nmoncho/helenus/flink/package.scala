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

package net.nmoncho.helenus

import java.io.DataInput
import java.io.DataOutput
import java.math.BigInteger

import com.datastax.oss.driver.api.core.CqlSession
import net.nmoncho.helenus.api.RowMapper
import net.nmoncho.helenus.api.cql.ScalaPreparedStatement
import net.nmoncho.helenus.flink.sink._
import net.nmoncho.helenus.flink.source._
import org.apache.flink.api.common.io.InputFormat
import org.apache.flink.api.common.io.OutputFormatBase
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.connector.source._
import org.apache.flink.api.java.DataSet
import org.apache.flink.api.java.ExecutionEnvironment
import org.apache.flink.api.java.operators.DataSink
import org.apache.flink.api.java.operators.DataSource
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.functions.sink.SinkFunction

package object flink {

  implicit class DataStreamOps[T](private val input: DataStream[T]) extends AnyVal {

    /** Adds a sink sending data to Cassandra
      *
      * Statements can be defined and handed over in curried form by using `toCQL(_)`, for example:
      * {{{
      *   dataStream.addCassandraSink(
      *     "INSERT INTO hotels(id, name, address) VALUES (?, ?, ?)".toCQL(_)
      *      .prepare[Long, String, Address].
      *     config
      *   )
      * }}}
      *
      * @param pstmt function taking a [[CqlSession]] and providing a [[ScalaPreparedStatement]]
      * @param config cassandra config
      * @return [[CassandraSink]]
      */
    def addCassandraSink[Out](
        pstmt: CqlSession => ScalaPreparedStatement[T, Out],
        config: CassandraSink.Config
    ): CassandraSink[T] = {
      val fn: SinkFunction[T] = asSinkFunction(pstmt, config)

      new CassandraSink(Right(input.addSink(fn).name("Cassandra Sink")))
    }

  }

  implicit class DataSetOps[T](private val input: DataSet[T]) extends AnyVal {

    /** Adds a sink sending data to Cassandra
      *
      * Statements can be defined and handed over in curried form by using `toCQL(_)`, for example:
      * {{{
      *   dataSet.addCassandraSink(
      *     "INSERT INTO hotels(id, name, address) VALUES (?, ?, ?)".toCQL(_)
      *      .prepare[Long, String, Address].
      *     config
      *   )
      * }}}
      *
      * @param pstmt  function taking a [[CqlSession]] and providing a [[ScalaPreparedStatement]]
      * @param config cassandra config
      * @return [[DataSink]]
      */
    def addCassandraOutput[Out](
        pstmt: CqlSession => ScalaPreparedStatement[T, Out],
        config: CassandraSink.Config
    ): DataSink[T] = {
      val fn: OutputFormatBase[T, Unit] = asOutputFormat(pstmt, config)

      input.output(fn).name("Cassandra Sink")
    }
  }

  implicit class ScalaBoundStatementBuilderFlinkReadSyncOps[Out](
      private val bstmt: CqlSession => ScalaBoundStatement[Out]
  ) extends AnyVal {

    /** Turns this [[ScalaBoundStatement]] builder into a Flink [[Source]]
      *
      * @param config
      * @param mapper how to map a Cassandra Row into an ouput
      * @param typeInformation element type information
      */
    def asSource(config: CassandraSource.Config)(
        implicit mapper: RowMapper[Out],
        typeInformation: TypeInformation[Out]
    ): Source[Out, CassandraSplit, CassandraEnumeratorState] =
      net.nmoncho.helenus.flink.source.asSource(bstmt, config)

    /** Turns this [[ScalaBoundStatement]] builder into a Flink [[InputFormat]]
      *
      * @param config
      * @param mapper how to map a Cassandra Row into an ouput
      */
    def asInputFormat(config: CassandraSource.Config)(
        implicit mapper: RowMapper[Out]
    ): InputFormat[Out, _] =
      net.nmoncho.helenus.flink.source.asInputFormat(bstmt, config)
  }

  implicit class ExecutionEnvironmentOps(private val env: ExecutionEnvironment) extends AnyVal {

    /** Invokes this [[ExecutionEnvironment.createInput]] provided that there is an implicit [[TypeInformation]]
      */
    def createDataSource[Out](inputFormat: InputFormat[Out, _])(
        implicit typeInfo: TypeInformation[Out]
    ): DataSource[Out] =
      env.createInput(inputFormat, typeInfo)

  }

  private[flink] def writeBigInt(bigInt: BigInt, output: DataOutput): Unit = {
    val bigIntegerBytes = bigInt.bigInteger.toByteArray
    output.writeInt(bigIntegerBytes.length)
    output.write(bigIntegerBytes)
  }

  private[flink] def readBigInt(input: DataInput): BigInt = {
    val bigIntegerSize  = input.readInt
    val bigIntegerBytes = new Array[Byte](bigIntegerSize)
    input.readFully(bigIntegerBytes)

    new BigInteger(bigIntegerBytes)
  }
}

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

package net.nmoncho.helenus.pekko

import scala.collection.immutable
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.datastax.oss.driver.api.core.CqlSession
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.nmoncho.helenus.api.RowMapper
import net.nmoncho.helenus.api.cql.Adapter
import net.nmoncho.helenus.utils.CassandraSpec
import org.apache.pekko.Done
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.connectors.cassandra.CassandraSessionSettings
import org.apache.pekko.stream.connectors.cassandra.CassandraWriteSettings
import org.apache.pekko.stream.connectors.cassandra.scaladsl.CassandraSession
import org.apache.pekko.stream.connectors.cassandra.scaladsl.CassandraSessionRegistry
import org.apache.pekko.stream.scaladsl.FlowWithContext
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Seconds
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpec

class PekkoConnectorSpec extends AnyWordSpec with Matchers with CassandraSpec with ScalaFutures {

  import PekkoConnectorSpec._
  import net.nmoncho.helenus._

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(Span(6, Seconds))

  private implicit lazy val system: ActorSystem =
    ActorSystem(
      "pekko-spec",
      cassandraConfig
    )

  private implicit lazy val as: CassandraSession = CassandraSessionRegistry(system)
    .sessionFor(CassandraSessionSettings())

  "Helenus" should {
    import system.dispatcher

    "work with Pekko Streams (sync)" in withSession { implicit session =>
      val query: Source[IceCream, NotUsed] = "SELECT * FROM ice_creams".toCQL.prepareUnit
        .as[IceCream]
        .asReadSource()

      val insert: Sink[IceCream, Future[Done]] =
        "INSERT INTO ice_creams(name, numCherries, cone) VALUES(?, ?, ?)".toCQL
          .prepare[String, Int, Boolean]
          .from[IceCream]
          .asWriteSink(writeSettings)

      testStream(ijes, query, insert)(identity)

      val queryName: Source[IceCream, NotUsed] = "SELECT * FROM ice_creams WHERE name = ?".toCQL
        .prepare[String]
        .as[IceCream]
        .asReadSource("vanilla")

      whenReady(queryName.runWith(Sink.seq[IceCream])) { result =>
        result should not be empty
      }

      val queryNameAndCone: Source[IceCream, NotUsed] =
        "SELECT * FROM ice_creams WHERE name = ? AND cone = ? ALLOW FILTERING".toCQL
          .prepare[String, Boolean]
          .as[IceCream]
          .asReadSource("vanilla", true)

      whenReady(queryNameAndCone.runWith(Sink.seq[IceCream])) { result =>
        result should not be empty
      }
    }

    "work with Pekko Streams and Context (sync)" in withSession { implicit session =>
      val query: Source[IceCream, NotUsed] = "SELECT * FROM ice_creams".toCQL.prepareUnit
        .as[IceCream]
        .asReadSource()

      val insert =
        "INSERT INTO ice_creams(name, numCherries, cone) VALUES(?, ?, ?)".toCQL
          .prepare[String, Int, Boolean]
          .from[IceCream]
          .asWriteFlowWithContext[String](writeSettings)

      testStreamWithContext(ijes, query, insert)(ij => ij -> ij.name)
    }

    "perform batched writes with Pekko Stream (sync)" in withSession { implicit session =>
      val query: Source[IceCream, NotUsed] = "SELECT * FROM ice_creams".toCQL.prepareUnit
        .as[IceCream]
        .asReadSource()

      val batchedInsert: Sink[IceCream, Future[Done]] =
        "INSERT INTO ice_creams(name, numCherries, cone) VALUES(?, ?, ?)".toCQL
          .prepare[String, Int, Boolean]
          .from[IceCream]
          .asWriteSinkBatched(writeSettings, _.name.charAt(0))

      testStream(batchIjs, query, batchedInsert)(identity)
    }

    "work with Pekko Streams (async)" in {
      val query: Source[IceCream, NotUsed] = "SELECT * FROM ice_creams".toAsyncCQL.prepareUnit
        .as[IceCream]
        .asReadSource()

      val insert: Sink[IceCream, Future[Done]] =
        "INSERT INTO ice_creams(name, numCherries, cone) VALUES(?, ?, ?)".toAsyncCQL
          .prepare[String, Int, Boolean]
          .from[IceCream]
          .asWriteSink(writeSettings)

      testStream(ijes, query, insert)(identity)
    }

    "work with Pekko Streams and Context (async)" in {
      val query: Source[IceCream, NotUsed] = "SELECT * FROM ice_creams".toAsyncCQL.prepareUnit
        .as[IceCream]
        .asReadSource()

      val insert =
        "INSERT INTO ice_creams(name, numCherries, cone) VALUES(?, ?, ?)".toAsyncCQL
          .prepare[String, Int, Boolean]
          .from[IceCream]
          .asWriteFlowWithContext[String](writeSettings)

      testStreamWithContext(ijes, query, insert)(ij => ij -> ij.name)

      val queryName: Source[IceCream, NotUsed] =
        "SELECT * FROM ice_creams WHERE name = ?".toAsyncCQL
          .prepare[String]
          .as[IceCream]
          .asReadSource("vanilla")

      whenReady(queryName.runWith(Sink.seq[IceCream])) { result =>
        result should not be empty
      }
    }

    "perform batched writes with Pekko Stream (async)" in {
      val query: Source[IceCream, NotUsed] = "SELECT * FROM ice_creams".toAsyncCQL.prepareUnit
        .as[IceCream]
        .asReadSource()

      val batchedInsert: Sink[IceCream, Future[Done]] =
        "INSERT INTO ice_creams(name, numCherries, cone) VALUES(?, ?, ?)".toAsyncCQL
          .prepare[String, Int, Boolean]
          .from[IceCream]
          .asWriteSinkBatched(writeSettings, _.name.charAt(0))

      testStream(batchIjs, query, batchedInsert)(identity)
    }
  }

  private def withSession(fn: CqlSession => Unit)(implicit ec: ExecutionContext): Unit =
    whenReady(as.underlying().map(fn))(_ => /* Do nothing, test should be inside */ ())

  /** Inserts data with a sink, and reads it back with source to compare it
    */
  private def testStream[T, U](
      data: immutable.Iterable[T],
      source: Source[T, NotUsed],
      sink: Sink[U, Future[Done]]
  )(fn: T => U): Unit = {
    import system.dispatcher

    val tx = for {
      // Write to DB
      _ <- Source(data).map(fn).runWith(sink)
      // Read from DB
      values <- source.runWith(Sink.seq)
    } yield values

    whenReady(tx) { dbValues =>
      dbValues.toSet shouldBe data.toSet
    }
  }

  /** Inserts data with a sink, and reads it back with source to compare it
    */
  private def testStreamWithContext[T, U, Ctx](
      data: immutable.Iterable[T],
      source: Source[T, NotUsed],
      flowWithContext: FlowWithContext[U, Ctx, U, Ctx, NotUsed]
  )(fn: T => (U, Ctx)): Unit = {
    import system.dispatcher

    val tx = for {
      // Write to DB
      _ <- Source(data).map(fn).via(flowWithContext).runWith(Sink.ignore)
      // Read from DB
      values <- source.runWith(Sink.seq)
    } yield values

    whenReady(tx) { dbValues =>
      dbValues.toSet shouldBe data.toSet
    }
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    executeDDL("""CREATE TABLE IF NOT EXISTS ice_creams(
                 |  name         TEXT PRIMARY KEY,
                 |  numCherries  INT,
                 |  cone         BOOLEAN
                 |)""".stripMargin)
  }

  private def cassandraConfig: Config = ConfigFactory
    .parseString(s"""
                    |datastax-java-driver.basic {
                    |  contact-points = ["$contactPoint"]
                    |  session-keyspace = "$keyspace"
                    |  load-balancing-policy.local-datacenter = "datacenter1"
                    |}""".stripMargin)
    .withFallback(ConfigFactory.load())
}

object PekkoConnectorSpec {

  case class IceCream(name: String, numCherries: Int, cone: Boolean)
  object IceCream {
    import net.nmoncho.helenus._
    implicit val rowMapper: RowMapper[IceCream] = RowMapper[IceCream]
    implicit val rowAdapter: Adapter[IceCream, (String, Int, Boolean)] =
      Adapter.builder[IceCream].build
  }

  private val writeSettings = CassandraWriteSettings.defaults

  private val ijes = List(
    IceCream("vanilla", numCherries    = 2, cone  = true),
    IceCream("chocolate", numCherries  = 0, cone  = false),
    IceCream("the answer", numCherries = 42, cone = true)
  )

  private val batchIjs = (0 until writeSettings.maxBatchSize).map { i =>
    val original = ijes(i % ijes.size)
    original.copy(name = s"${original.name} $i")
  }.toSet
}

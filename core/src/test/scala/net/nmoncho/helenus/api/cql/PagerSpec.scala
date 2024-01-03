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
package api.cql

import java.time.LocalDate

import scala.annotation.nowarn
import scala.util.Failure
import scala.util.Success

import com.datastax.oss.driver.api.core.CqlSession
import net.nmoncho.helenus.models.Address
import net.nmoncho.helenus.models.Hotel
import net.nmoncho.helenus.utils.CassandraSpec
import net.nmoncho.helenus.utils.HotelsTestData
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Seconds
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpec

@nowarn("cat=unused-imports")
class PagerSpec
    extends AnyWordSpec
    with Matchers
    with Eventually
    with CassandraSpec
    with ScalaFutures {

  import HotelsTestData._
  import scala.collection.compat._ // Don't remove me

  import scala.concurrent.ExecutionContext.Implicits.global

  private implicit lazy val cqlSession: CqlSession = session

  private implicit val hotelAdapter
      : Adapter[Hotel, (String, String, String, Address, Set[String])] = Adapter[Hotel]

  "ScalaPreparedStatement Pager" should {
    "page a unit query" in {
      val pageSize = 2
      val pstmt    = "SELECT * FROM hotels".toCQL.prepareUnit.as[Hotel]
      val pager0   = pstmt.pager()

      // First Page
      val (pager1, page0) = pager0.execute(pageSize)
      val page0Hotels     = page0.toSeq
      page0Hotels should have size pageSize
      page0Hotels.foreach(hotel => Hotels.all should contain(hotel))
      pager1.hasMorePages shouldBe true

      // Second Page
      val (pager2, page1) = pager1.execute(pageSize)
      val page1Hotels     = page1.toSeq
      page1Hotels should have size pageSize
      page1Hotels.foreach(hotel => Hotels.all should contain(hotel))
      pager2.hasMorePages shouldBe true

      // Third (Last) Page
      val (pager3, page2) = pager2.execute(pageSize)
      val page2Hotels     = page2.toSeq
      page2Hotels should have size 1
      page2Hotels.foreach(hotel => Hotels.all should contain(hotel))
      pager3.hasMorePages shouldBe false

      withClue("requesting a page after the last page should be empty") {
        val (_, page3)  = pager3.execute(2)
        val page3Hotels = page3.toSeq
        page3Hotels shouldBe empty
      }

      withClue("a page could be fetched again") {
        val (_, page1A)  = pager1.execute(pageSize)
        val page1AHotels = page1A.toSeq
        page1AHotels shouldEqual page1Hotels
      }

      withClue("resume from string form") {
        pstmt.pager(pager1.encodePagingState.get) match {
          case Success(value) =>
            val (_, page1A)  = value.execute(pageSize)
            val page1AHotels = page1A.toSeq
            page1AHotels shouldEqual page1Hotels

          case Failure(exception) =>
            fail("state should be decoded", exception)
        }
      }
    }

    "page a single param query" in {
      val pageSize = 2
      val pstmt =
        "SELECT date, room_number, is_available FROM available_rooms_by_hotel_date WHERE hotel_id = ?".toCQL
          .prepare[String]
          .as[(LocalDate, Short, Boolean)]
      val pager0 = pstmt.pager(Hotels.h1.id)

      val (pager1, page0) = pager0.execute(pageSize)
      val page0Results    = page0.toSeq
      page0Results should have size pageSize
      pager1.hasMorePages shouldBe true

      val (pager2, page1) = pager1.execute(pageSize)
      val page1Results    = page1.toSeq
      page1Results should have size pageSize
      pager2.hasMorePages shouldBe true

      withClue("resume from string form") {
        pstmt.pager(pager1.encodePagingState.get, Hotels.h1.id) match {
          case Success(value) =>
            val (_, page1A)   = value.execute(pageSize)
            val page1AResults = page1A.toSeq
            page1AResults shouldEqual page1Results

          case Failure(exception) =>
            fail("state should be decoded", exception)
        }
      }

      withClue("fail on invalid string form") {
        val pstmt = "SELECT * FROM hotels".toCQL.prepareUnit.as[Hotel]
        pstmt.pager(pager1.encodePagingState.get) match {
          case Success(value) =>
            fail("not here")

          case Failure(exception) =>
          // all good
        }
      }
    }
  }

  "ScalaPreparedStatement Pager (async)" should {
    "page a unit query" in {
      val pageSize = 2
      val pager0   = "SELECT * FROM hotels".toCQLAsync.prepareUnit.as[Hotel].pager()

      val (pager1, page0) = whenReady(pager0.executeAsync(pageSize))(identity)
      val page0Hotels     = page0.toSeq
      page0Hotels should have size pageSize
      page0Hotels.foreach(hotel => Hotels.all should contain(hotel))
      pager1.hasMorePages shouldBe true

      val (pager2, page1) = whenReady(pager1.executeAsync(pageSize))(identity)
      val page1Hotels     = page1.toSeq
      page1Hotels should have size pageSize
      page1Hotels.foreach(hotel => Hotels.all should contain(hotel))
      pager2.hasMorePages shouldBe true

      val (pager3, page2) = whenReady(pager2.executeAsync(pageSize))(identity)
      val page2Hotels     = page2.toSeq
      page2Hotels should have size 1
      page2Hotels.foreach(hotel => Hotels.all should contain(hotel))
      pager3.hasMorePages shouldBe false

      withClue("requesting a page after the last page should be empty") {
        val (_, page3)  = whenReady(pager3.executeAsync(pageSize))(identity)
        val page3Hotels = page3.toSeq
        page3Hotels shouldBe empty
      }

      withClue("a page could be fetched again") {
        val (_, page1A)  = whenReady(pager1.executeAsync(pageSize))(identity)
        val page1AHotels = page1A.toSeq
        page1AHotels shouldEqual page1Hotels
      }
    }

    "page a single param query" in {
      val pageSize = 2
      val pager0 =
        "SELECT date, room_number, is_available FROM available_rooms_by_hotel_date WHERE hotel_id = ?".toCQLAsync
          .prepare[String]
          .as[(LocalDate, Short, Boolean)]
          .pager(Hotels.h1.id)

      val (pager1, page0) = whenReady(pager0.executeAsync(pageSize))(identity)
      val page0Results    = page0.toSeq
      page0Results should have size pageSize
      pager1.hasMorePages shouldBe true

      val (pager2, page1) = whenReady(pager1.executeAsync(pageSize))(identity)
      val page1Results    = page1.toSeq
      page1Results should have size pageSize
      pager2.hasMorePages shouldBe true
    }
  }

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(Span(6, Seconds))

  override def beforeAll(): Unit = {
    super.beforeAll()
    executeFile("hotels.cql")
    insertTestData()
  }

  override def afterEach(): Unit = {
    // Don't truncate keyspace
  }
}

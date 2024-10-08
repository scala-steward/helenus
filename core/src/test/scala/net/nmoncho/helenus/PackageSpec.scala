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

import scala.util.Success

import com.datastax.oss.driver.internal.core.`type`.DefaultListType
import com.datastax.oss.driver.internal.core.`type`.PrimitiveType
import com.datastax.oss.protocol.internal.ProtocolConstants
import net.nmoncho.helenus.models.Address
import net.nmoncho.helenus.utils.CassandraSpec
import org.scalatest.OptionValues._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class PackageSpec extends AnyWordSpec with Matchers with CassandraSpec {

  "Package" should {

    "get keyspace by name" in {
      val sessionKeyspace = session.keyspace(keyspace)
      sessionKeyspace shouldBe defined
      sessionKeyspace.value.getName.asInternal() shouldEqual keyspace

      sessionKeyspace shouldEqual session.sessionKeyspace

      session.keyspace("another_keyspace") should not be defined
    }

    "get Execution Profiles" in {
      val defaultProfile = session.executionProfile("default")
      defaultProfile shouldBe defined
      defaultProfile.value.getName shouldEqual "default"

      session.executionProfile("another-profile") should not be defined
    }

    "register codecs" in {
      val listStringCodec = Codec.of[List[String]]

      session.registerCodecs(listStringCodec) shouldBe a[Success[_]]

      session.getContext.getCodecRegistry
        .codecFor(
          new DefaultListType(new PrimitiveType(ProtocolConstants.DataType.VARCHAR), true)
        ) shouldBe listStringCodec
    }

    "register UDT codecs" in {
      val keyspace = session.sessionKeyspace.map(_.getName.asInternal()).getOrElse("")

      withClue("registering codec with a keyspace") {
        session.registerCodecs(Codec.of[Address](keyspace)) shouldBe a[Success[_]]
      }

      withClue("registering codec without a specific keyspace") {
        session.registerCodecs(Codec.of[Address]()) shouldBe a[Success[_]]
      }
    }
  }

}

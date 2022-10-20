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

package net.nmoncho.helenus.docs

import com.datastax.oss.driver.api.core.CqlSession
import org.cassandraunit.utils.EmbeddedCassandraServerHelper

object DocsHelper {

  final val keyspace: String = "docs"

  private var started = false

  def startTimeout: Long = EmbeddedCassandraServerHelper.DEFAULT_STARTUP_TIMEOUT * 3

  private def startCassandra(): Unit = {
    EmbeddedCassandraServerHelper.startEmbeddedCassandra(startTimeout)

    val session = EmbeddedCassandraServerHelper.getSession
    session.execute(
      s"CREATE KEYSPACE IF NOT EXISTS $keyspace WITH replication = {'class':'SimpleStrategy', 'replication_factor' : 1}"
    )
    session.execute(s"USE $keyspace")
    session.execute(
      s"""CREATE TABLE population_by_country(
         |  country TEXT,
         |  age INT,
         |  amount INT,
         |  PRIMARY KEY (country, age)
         |) WITH CLUSTERING ORDER BY (age ASC)""".stripMargin
    )
    session.execute(
      "INSERT INTO population_by_country(country, age, amount) VALUES ('nl', 18, 1000)"
    )
    session.execute(
      "INSERT INTO population_by_country(country, age, amount) VALUES ('nl', 21, 900)"
    )
    session.execute(
      "CREATE TYPE IF NOT EXISTS ice_cream (name TEXT, num_cherries INT, cone BOOLEAN)"
    )
  }

  def cqlSession: CqlSession = this.synchronized {
    if (!started) {
      startCassandra()
      started = true
    }

    EmbeddedCassandraServerHelper.getSession
  }

}

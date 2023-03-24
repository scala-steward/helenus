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

package net.nmoncho

import scala.annotation.nowarn
import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.Failure
import scala.util.Try

import com.datastax.dse.driver.api.core.cql.reactive.ReactiveResultSet
import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.MappedAsyncPagingIterable
import com.datastax.oss.driver.api.core.PagingIterable
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.`type`.codec.registry.MutableCodecRegistry
import com.datastax.oss.driver.api.core.cql.AsyncResultSet
import com.datastax.oss.driver.api.core.cql.BoundStatement
import com.datastax.oss.driver.api.core.cql.ResultSet
import com.datastax.oss.driver.api.core.cql.Row
import com.datastax.oss.driver.api.core.metadata.schema.KeyspaceMetadata
import net.nmoncho.helenus.api.RowMapper
import net.nmoncho.helenus.api.`type`.codec.CodecDerivation
import net.nmoncho.helenus.api.cql.Adapter
import net.nmoncho.helenus.internal.cql.ParameterValue
import net.nmoncho.helenus.internal.cql.ScalaPreparedStatement
import net.nmoncho.helenus.internal.cql.ScalaPreparedStatement.CQLQuery
import net.nmoncho.helenus.internal.reactive.MapOperator
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory

package object helenus extends CodecDerivation {

  private val log = LoggerFactory.getLogger("net.nmoncho.helenus")

  implicit class ClqSessionOps(private val session: CqlSession) extends AnyVal {

    def sessionKeyspace: Option[KeyspaceMetadata] = {
      val opt: java.util.Optional[String] = session.getKeyspace.map(_.asInternal())

      if (opt.isPresent) keyspace(opt.get())
      else None
    }

    def keyspace(name: String): Option[KeyspaceMetadata] = {
      val opt = session.getMetadata.getKeyspace(name)

      if (opt.isPresent) Some(opt.get())
      else None
    }

    /** Registers codecs in the Session's CodecRegistry.
      */
    def registerCodecs(codecs: TypeCodec[_]*): Try[Unit] =
      session.getContext.getCodecRegistry match {
        case mutableRegistry: MutableCodecRegistry =>
          Try(mutableRegistry.register(codecs: _*))

        // $COVERAGE-OFF$
        case _ =>
          Failure(new IllegalStateException("CodecRegistry isn't mutable"))
        // $COVERAGE-ON$
      }

  }

  /** Creates a [[BoundStatement]] using String Interpolation.
    * There is also an asynchronous alternative, which is `asyncCql` instead of `cql`.
    *
    * This won't execute the bound statement yet, just set its arguments.
    *
    * {{{
    * import net.nmoncho.helenus._
    *
    * val id = UUID.fromString("...")
    * val bstmt = cql"SELECT * FROM some_table WHERE id = $id"
    * }}}
    */
  implicit class CqlStringInterpolation(private val sc: StringContext) extends AnyVal {

    def cql(args: ParameterValue*)(implicit session: CqlSession): BoundStatement = {
      val query = cqlQuery(args)

      val bstmt = session.prepare(query).bind()
      setParameters(bstmt, args)
    }

    def asyncCql(
        args: ParameterValue*
    )(implicit session: CqlSession, ec: ExecutionContext): Future[BoundStatement] = {
      import net.nmoncho.helenus.internal.compat.FutureConverters._

      val query = cqlQuery(args)

      session.prepareAsync(query).asScala.map { pstmt =>
        setParameters(pstmt.bind(), args)
      }
    }

    private def setParameters(bstmt: BoundStatement, args: Seq[ParameterValue]): BoundStatement =
      args
        .foldLeft(bstmt -> 0) { case ((bstmt, index), arg) =>
          arg.set(bstmt, index) -> (index + 1)
        }
        ._1

    private def cqlQuery(args: Seq[ParameterValue]): String = {
      val partsIt = sc.parts.iterator
      val argsIt  = args.iterator

      val sb = new mutable.StringBuilder(partsIt.next())
      while (argsIt.hasNext) {
        sb.append(argsIt.next().toCQL)
          .append(partsIt.next())
      }

      sb.toString()
    }

  }

  /** Extension methods for [[BoundStatement]], helping you execute them with the proper context.
    */
  implicit class BoundStatementSyncOps(private val bstmt: BoundStatement) extends AnyVal {
    import net.nmoncho.helenus.internal.compat.FutureConverters._

    def execute()(implicit session: CqlSession): ResultSet =
      session.execute(bstmt)

    def executeAsync()(implicit session: CqlSession): Future[AsyncResultSet] =
      session.executeAsync(bstmt).asScala

    def executeReactive()(implicit session: CqlSession): ReactiveResultSet =
      session.executeReactive(bstmt)
  }

  implicit class PreparedStatementSyncStringOps(private val query: String) extends AnyVal {

    def toCQL(implicit session: CqlSession): CQLQuery =
      CQLQuery(
        query,
        session
      )

    def toAsyncCQL(
        implicit futSession: Future[CqlSession],
        ec: ExecutionContext
    ): Future[CQLQuery] =
      futSession.map(CQLQuery(query, _))
  }

  implicit class RowOps(private val row: Row) extends AnyVal {
    def as[T](implicit mapper: RowMapper[T]): T = mapper.apply(row)
  }

  implicit class ResultSetOps(private val rs: ResultSet) extends AnyVal {
    def as[T](implicit mapper: RowMapper[T]): PagingIterable[T] = rs.map(mapper.apply)
  }

  implicit class AsyncResultSetOps(private val rs: AsyncResultSet) extends AnyVal {
    def as[T](implicit mapper: RowMapper[T]): MappedAsyncPagingIterable[T] = rs.map(mapper.apply)
  }

  implicit class ReactiveResultSetOpt(private val rrs: ReactiveResultSet) extends AnyVal {
    def as[T](implicit mapper: RowMapper[T]): Publisher[T] = {
      val op = new MapOperator(rrs, mapper.apply)

      op.publisher
    }
  }

  /** Extension methods for [[PagingIterable]]
    *
    * Mostly how to transform this Cassandra iterable into a more Scala idiomatic structure.
    */
  implicit class PagingIterableOps[T](private val pi: PagingIterable[T]) extends AnyVal {
    import scala.collection.compat._

    /** Next potential element of this iterable
      */
    def nextOption(): Option[T] = Option(pi.one())

    /** This [[PagingIterable]] as a Scala [[Iterator]]
      */
    def iter: Iterator[T] = {
      import scala.jdk.CollectionConverters._

      pi.iterator().asScala
    }

    /** This [[PagingIterable]] as a Scala Collection; <b>not recommended for queries that return a
      * large number of elements</b>.
      *
      * Example
      * {{{
      *   import scala.collection.compat._ // Only for Scala 2.12
      *
      *   pagingIterable.to(List)
      *   pagingIterable.to(Set)
      * }}}
      */
    def to[Col[_]](factory: Factory[T, Col[T]])(
        implicit cbf: BuildFrom[Nothing, T, Col[T]]
    ): Col[T] = iter.to(factory)
  }

  implicit class MappedAsyncPagingIterableOps[T](private val pi: MappedAsyncPagingIterable[T])
      extends AnyVal {
    import net.nmoncho.helenus.internal.compat.FutureConverters._

    import scala.jdk.CollectionConverters._

    /** Current page as a Scala [[Iterator]]
      */
    def currPage: Iterator[T] = pi.currentPage().iterator().asScala

    /** Fetches and returns the next page as a Scala [[Iterator]]
      */
    def nextPage(implicit ec: ExecutionContext): Future[Iterator[T]] =
      if (pi.hasMorePages) {
        pi.fetchNextPage().asScala.map(_.currPage)
      } else {
        Future.successful(Iterator())
      }

    /** Return all results of this [[MappedAsyncPagingIterable]] as a Scala [[Iterator]],
      * without having to request more pages.
      *
      * It will fetch the next page in a blocking fashion after it has exhausted the current page.
      * <b>NOTE:</b> On Scala 2.12 it will fetch all pages!
      *
      * @param timeout how much time to wait for the next page to be ready
      * @param ec
      */
    @nowarn("cat=unused-imports")
    def iter(timeout: FiniteDuration)(implicit ec: ExecutionContext): Iterator[T] = { // Don't remove me
      import scala.collection.compat._ // Don't remove me
      // FIXME Using `TraversableOnce` Scala 2.12, also it doesn't lazily concat iterators
      // since `compat` implementation is different
      def concat(): TraversableOnce[T] =
        pi
          .currentPage()
          .iterator()
          .asScala
          .concat {
            if (pi.hasMorePages) {
              log.debug("fetching more pages")
              Await.ready(pi.fetchNextPage().asScala, timeout)

              concat()
            } else {
              log.debug("no more pages")
              Iterator()
            }
          }

      concat().iterator
    }
  }

  implicit class FutureScalaPrepareStatementOps[U, T](
      private val fut: Future[ScalaPreparedStatement[U, T]]
  ) extends AnyVal {
    def from[A](
        implicit ec: ExecutionContext,
        extractor: Adapter[A, U]
    ): Future[ScalaPreparedStatement[A, T]] =
      fut.map(_.from[A])
  }
}

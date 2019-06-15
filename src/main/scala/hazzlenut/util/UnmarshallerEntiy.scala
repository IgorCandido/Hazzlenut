package hazzlenut.util

import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.stream.Materializer
import hazzlenut.errors.HazzlenutError
import hazzlenut.errors.HazzlenutError.ThrowableError
import scalaz.zio.{IO, ZIO}

import scala.concurrent.Future

trait UnmarshallerEntiy[F[_]] {
  def unmarshal[T, S](entity: T)(implicit materializer: Materializer,
                                 unmarshaller: Unmarshaller[T, S]): F[S]
}

object UnmarshallerEntiy {
  def apply[F[_]](implicit F: UnmarshallerEntiy[F]): UnmarshallerEntiy[F] = F

  implicit val unmarshallerFuture = new UnmarshallerEntiy[Future] {
    override def unmarshal[T, S](entity: T)(
      implicit materializer: Materializer,
      unmarshaller: Unmarshaller[T, S]
    ): Future[S] =
      Unmarshal(entity)
        .to[S]
  }

  implicit val unmarshallerZIO =
    new UnmarshallerEntiy[IO[HazzlenutError, ?]] {
      override def unmarshal[T, S](entity: T)(
        implicit materializer: Materializer,
        unmarshaller: Unmarshaller[T, S]
      ): ZIO[Any, HazzlenutError, S] =
        ZIO
          .fromFuture { _ =>
            unmarshallerFuture.unmarshal(entity)
          }
          .mapError { throwable =>
            ThrowableError(throwable)
          }
    }
}

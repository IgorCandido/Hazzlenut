package hazzlenut.util

import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.stream.Materializer
import cats.MonadError
import hazzlenut.errors.HazzlenutError
import hazzlenut.errors.HazzlenutError.{ThrowableError, UnmarshallError}
import scalaz.zio.{IO, ZIO}

import scala.concurrent.Future

trait UnmarshallerEntiy[F[_]] {
  protected def unmarshalInternal[T, S](
    entity: T
  )(implicit materializer: Materializer, unmarshaller: Unmarshaller[T, S]): F[S]

  def unmarshal[T, S](entity: T)(
    implicit materializer: Materializer,
    unmarshaller: Unmarshaller[T, S],
    monadError: MonadError[F, HazzlenutError]
  ): F[S] = {
    monadError.recoverWith(unmarshalInternal(entity)) {
      case ThrowableError(error) =>
        monadError.raiseError(UnmarshallError(error))
      case error => monadError.raiseError(UnmarshallError(error))
    }
  }
}

object UnmarshallerEntiy {
  def apply[F[_]](implicit F: UnmarshallerEntiy[F]): UnmarshallerEntiy[F] = F

  implicit val unmarshallerFuture = new UnmarshallerEntiy[Future] {
    override def unmarshalInternal[T, S](entity: T)(
      implicit materializer: Materializer,
      unmarshaller: Unmarshaller[T, S]
    ): Future[S] =
      Unmarshal(entity)
        .to[S]
  }

  implicit val unmarshallerZIO =
    new UnmarshallerEntiy[IO[HazzlenutError, ?]] {
      override def unmarshalInternal[T, S](entity: T)(
        implicit materializer: Materializer,
        unmarshaller: Unmarshaller[T, S]
      ): ZIO[Any, HazzlenutError, S] =
        ZIO
          .fromFuture { _ =>
            unmarshallerFuture.unmarshalInternal(entity)
          }
          .mapError { throwable =>
            UnmarshallError(throwable)
          }
    }
}

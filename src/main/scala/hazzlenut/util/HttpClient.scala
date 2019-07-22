package hazzlenut.util

import cats.implicits._
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import cats.{Monad, MonadError}
import hazzlenut.errors.HazzlenutError
import hazzlenut.errors.HazzlenutError.{
  ConnectionError,
  HttpError,
  ThrowableError
}
import scalaz.zio.{IO, ZIO}

import scala.concurrent.Future

object HttpClient {
  implicit class HandleError[F[_]](val innerF: F) extends AnyVal {
    def recoverT(f: F[HttpResponse] => F[HttpResponse]) = f(innerF)
  }
}

trait HttpClient[F[_]] {
  import HttpClient._

  def request(request: HttpRequest, f: (HttpResponse) => String)(
    implicit monad: Monad[F],
    monadError: MonadError[F, Throwable]
  ): F[HttpResponse] = {
    (for {
      result <- httpRequest(request)
      reply <- handleResponse(result, f)
    } yield reply).recoverT { handleError }
  }

  protected def httpRequest(httpRequest: HttpRequest): F[HttpResponse]

  protected def handleError(
    f: F[HttpResponse]
  )(implicit monadError: MonadError[F, Throwable]): F[HttpResponse] = {
    monadError.handleErrorWith(f) { error =>
      monadError.raiseError(ConnectionError(error))
    }
  }

  protected def handleResponse(
    httpResponse: HttpResponse,
    f: (HttpResponse) => String
  )(implicit monadError: MonadError[F, Throwable]): F[HttpResponse] = {
    httpResponse.status match {
      case status if status.isSuccess() => monadError.pure(httpResponse)
      case anyStatus =>
        monadError.raiseError(HttpError(anyStatus.intValue(), f(httpResponse)))
    }
  }
}

object HttpClient {

  def apply[F[_]](implicit F: HttpClient[F]): HttpClient[F] = F

  implicit def httpClientFuture(implicit actorSystem: ActorSystem) =
    new HttpClient[Future] {
      override def httpRequest(httpRequest: HttpRequest): Future[HttpResponse] =
        Http()
          .singleRequest(httpRequest)
    }

  implicit def httpClientZIO(implicit actorSystem: ActorSystem,
                             monad: Monad[Future],
                             monadError: MonadError[Future, Throwable]) =
    new HttpClient[ZIO[Any, HazzlenutError, ?]] {
      override def httpRequest(
        httpRequest: HttpRequest
      ): IO[HazzlenutError, HttpResponse] = {
        IO.fromFuture { _ =>
            httpClientFuture.request(httpRequest,) // Provide a function that deserializes the entity from the httpResponse to a message
          }
          .mapError { throwable =>
            ThrowableError(throwable)
          }
      }
    }
}

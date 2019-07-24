package hazzlenut.util

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import cats.implicits._
import cats.{Monad, MonadError}
import hazzlenut.errors.HazzlenutError
import hazzlenut.errors.HazzlenutError.{
  ConnectionError,
  HttpError,
  ThrowableError
}
import scalaz.zio.{IO, ZIO}

import scala.concurrent.Future

trait HttpClient[F[_]] {
  import HttpClient._

  def request(request: HttpRequest, f: (HttpResponse) => F[String])(
    implicit monad: Monad[F],
    monadError: MonadError[F, HazzlenutError]
  ): F[HttpResponse] = {
    (for {
      result <- httpRequest(request)
      reply <- handleResponse(result, f)
    } yield reply).recoverT { handleError _ } //TODO research widening the MonadError to handle Throwables with the Hazzlenut MonadError
  }

  protected def httpRequest(httpRequest: HttpRequest): F[HttpResponse]

  protected def handleError(
    f: F[HttpResponse]
  )(implicit monadError: MonadError[F, HazzlenutError]): F[HttpResponse] = {
    monadError.handleErrorWith(f) { error =>
      error match {
        case ThrowableError(throwable) => monadError.raiseError(ConnectionError(throwable))
        case err => monadError.raiseError(ConnectionError(err)) //TODO check if it makes sense to return an hazzlenut error without treating it
      }
    }
  }

  protected def handleResponse(
    httpResponse: HttpResponse,
    f: (HttpResponse) => F[String]
  )(implicit monadError: MonadError[F, HazzlenutError]): F[HttpResponse] = {
    httpResponse.status match {
      case status if status.isSuccess() => monadError.pure(httpResponse)
      case anyStatus =>
        (for {
          errorMessage <- f(httpResponse: HttpResponse)
        } yield
          monadError.raiseError[HttpResponse](
            HttpError(anyStatus.intValue(), errorMessage)
          )).flatten
    }
  }
}

object HttpClient {

  def apply[F[_]](implicit F: HttpClient[F]): HttpClient[F] = F

  def requestHttp(
    httpRequest: HttpRequest
  )(implicit actorSystem: ActorSystem): Future[HttpResponse] =
    Http()
      .singleRequest(httpRequest)

  implicit class HandleError[F[_], A](val innerF: F[A]) extends AnyVal {
    def recoverT(f: F[A] => F[A]) = f(innerF)
  }

  implicit def httpClientFuture(implicit actorSystem: ActorSystem) =
    new HttpClient[Future] {
      override def httpRequest(httpRequest: HttpRequest): Future[HttpResponse] =
        requestHttp(httpRequest)
    }

  implicit def httpClientZIO(
    implicit actorSystem: ActorSystem
  ) =
    new HttpClient[ZIO[Any, HazzlenutError, ?]] {
      override def httpRequest(
        httpRequest: HttpRequest
      ): IO[HazzlenutError, HttpResponse] = {
        IO.fromFuture { _ =>
            requestHttp(httpRequest)
          }
          .mapError { throwable =>
            ThrowableError(throwable)
          }
      }
    }
}

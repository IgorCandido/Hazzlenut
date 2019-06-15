package hazzlenut.util

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import hazzlenut.errors.HazzlenutError
import hazzlenut.errors.HazzlenutError.ThrowableError
import scalaz.zio.{IO, ZIO}

import scala.concurrent.Future

trait HttpClient[F[_]] {
  def request(httpRequest: HttpRequest): F[HttpResponse]
}

object HttpClient {

  def apply[F[_]](implicit F: HttpClient[F]): HttpClient[F] = F

  implicit def httpClientFuture(implicit actorSystem: ActorSystem) =
    new HttpClient[Future] {
      override def request(httpRequest: HttpRequest): Future[HttpResponse] =
        Http()
          .singleRequest(httpRequest)
    }

  implicit def httpClientZIO(implicit actorSystem: ActorSystem) =
    new HttpClient[ZIO[Any, HazzlenutError, ?]] {
      override def request(
        httpRequest: HttpRequest
      ): IO[HazzlenutError, HttpResponse] = {
        IO.fromFuture { _ =>
            httpClientFuture.request(httpRequest)
          }
          .mapError { throwable =>
            ThrowableError(throwable)
          }
      }
    }
}

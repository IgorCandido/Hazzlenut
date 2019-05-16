package hazzlenut.services.twitch

import akka.actor.ActorSystem
import akka.stream.Materializer
import cats.Monad
import cats.data.Reader
import cats.Monad
import cats.data.Reader
import cats.implicits._

import scala.concurrent.ExecutionContext

object Authenticate {
  import OAuth.dsl._
  import Configuration.dsl._

  def getUrlToAuthenticate[F[_]: OAuth: Configuration: Monad](
    implicit system: ActorSystem,
    ec: ExecutionContext,
    mat: Materializer
  ): F[Option[String]] =
    for {
      config <- get
      urlMaybe <- getClientRedirectUrl(config)
    } yield urlMaybe

  def authenticate[F[_]: OAuth: Configuration: Monad](
    implicit system: ActorSystem,
    ec: ExecutionContext,
    mat: Materializer
  ): Reader[String, F[String]] = Reader {
    case code =>
      for {
        config <- get
        accessToken <- obtainAccessToken(code, config)
      } yield accessToken.token
  }
}

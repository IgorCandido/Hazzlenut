package hazzlenut.services.twitch

import akka.actor.ActorSystem
import akka.stream.Materializer
import cats.{Monad, MonadError}
import cats.data.Reader
import cats.implicits._
import hazzlenut.errors.HazzlenutError
import hazzlenut.errors.HazzlenutError.MonadErrorHazzlenut

import scala.concurrent.ExecutionContext

object Authenticate {
  import Configuration.dsl._
  import OAuth.dsl._

  def getUrlToAuthenticate[F[_]: OAuth: Configuration: Monad: MonadErrorHazzlenut](
    implicit system: ActorSystem,
    ec: ExecutionContext,
    mat: Materializer
  ): F[Option[String]] =
    for {
      config <- get[F]
      urlMaybe <- getClientRedirectUrl(config)
    } yield urlMaybe

  def authenticate[F[_]: OAuth: Configuration: Monad: MonadErrorHazzlenut](
    implicit system: ActorSystem,
    ec: ExecutionContext,
    mat: Materializer
  ): Reader[String, F[AccessToken]] = Reader {
    case code =>
      for {
        config <- get[F]
        accessToken <- obtainAccessToken(code, config)
      } yield accessToken
  }
}

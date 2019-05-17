package hazzlenut.services.twitch

import akka.actor.ActorSystem
import akka.stream.Materializer
import cats.{Monad, MonadError}
import cats.data.Reader
import cats.implicits._
import hazzlenut.errors.HazzlenutError

import scala.concurrent.ExecutionContext

object Authenticate {
  import Configuration.dsl._
  import OAuth.dsl._

  def getUrlToAuthenticate[F[_]: OAuth: Configuration: Monad](
    implicit system: ActorSystem,
    ec: ExecutionContext,
    mat: Materializer,
    mError: MonadError[F, HazzlenutError]
  ): F[Option[String]] =
    for {
      config <- get(implicitly[Monad[F]], implicitly[Configuration[F]], mError)
      urlMaybe <- getClientRedirectUrl(config)
    } yield urlMaybe

  def authenticate[F[_]: OAuth: Configuration: Monad](
    implicit system: ActorSystem,
    ec: ExecutionContext,
    mat: Materializer,
    mError: MonadError[F, HazzlenutError]
  ): Reader[String, F[String]] = Reader {
    case code =>
      for {
        config <- get(implicitly[Monad[F]], implicitly[Configuration[F]], mError)
        accessToken <- obtainAccessToken(code, config)
      } yield accessToken.token
  }
}

package hazzlenut.services.twitch

import akka.actor.ActorSystem
import akka.stream.Materializer
import cats.Monad
import cats.data.Reader
import cats.implicits._
import hazzlenut.services.twitch.Configuration.Config

import scala.concurrent.ExecutionContext

case class TwitchAppCredentials(clientId: String, clientSecret: String)

case class AccessToken(token: String)

trait OAuth[F[_]] {
  def getAuthorizeUrl(config: Config)(
    implicit system: ActorSystem,
    ec: ExecutionContext,
    mat: Materializer
  ): F[Option[String]]

  def obtainAccessToken(code: String, config: Config)(
    implicit system: ActorSystem,
    ec: ExecutionContext,
    mat: Materializer
  ): F[AccessToken]
}

object OAuth {
  def apply[F[_]](implicit F: OAuth[F]) = F

  object dsl {
    def obtainAccessToken[F[_]: OAuth](code: String, config: Config)(
      implicit system: ActorSystem,
      ec: ExecutionContext,
      mat: Materializer
    ): F[AccessToken] =
      OAuth[F].obtainAccessToken(code, config)

    def getClientRedirectUrl[F[_]: OAuth](config: Config)(
      implicit system: ActorSystem,
      ec: ExecutionContext,
      mat: Materializer
    ): F[Option[String]] =
      OAuth[F].getAuthorizeUrl(config)
  }
}

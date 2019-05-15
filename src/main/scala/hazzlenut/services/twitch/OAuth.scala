package hazzlenut.services.twitch

import akka.actor.ActorSystem
import akka.stream.Materializer
import cats.Monad
import cats.data.Reader
import cats.implicits._

import scala.concurrent.ExecutionContext

case class TwitchAppCredentials(clientId: String, clientSecret: String)

case class AccessToken(token: String)

trait OAuth[F[_]] {
  def getAuthorizeUrl(credential: TwitchAppCredentials)(
    implicit system: ActorSystem,
    ec: ExecutionContext,
    mat: Materializer
  ): F[Option[String]]

  def obtainAccessToken(credential: TwitchAppCredentials, code: String)(
    implicit system: ActorSystem,
    ec: ExecutionContext,
    mat: Materializer
  ): F[AccessToken]
}

object OAuth {
  def apply[F[_]](implicit F: OAuth[F]) = F

  object dsl {
    def obtainAccessToken[F[_]: OAuth](credential: TwitchAppCredentials,
                                       code: String)(
      implicit system: ActorSystem,
      ec: ExecutionContext,
      mat: Materializer
    ): F[AccessToken] =
      OAuth[F].obtainAccessToken(credential, code)

    def getClientRedirectUrl[F[_]: OAuth](credential: TwitchAppCredentials)(
      implicit system: ActorSystem,
      ec: ExecutionContext,
      mat: Materializer
    ): F[Option[String]] =
      OAuth[F].getAuthorizeUrl(credential)
  }
}

object Authenticate {
  import OAuth.dsl._

  def getUrlToAuthenticate[F[_]: OAuth: Monad](
    implicit system: ActorSystem,
    ec: ExecutionContext,
    mat: Materializer
  ): Reader[TwitchAppCredentials, F[Option[String]]] = Reader { credentials =>
    (for {
      urlMaybe <- getClientRedirectUrl(credentials)
    } yield urlMaybe)
  }

  def authenticate[F[_]: OAuth: Monad](
    implicit system: ActorSystem,
    ec: ExecutionContext,
    mat: Materializer
  ): Reader[(TwitchAppCredentials, String), F[String]] = Reader {
    case (credential, code) =>
      for {
        accessToken <- obtainAccessToken(credential, code)
      } yield accessToken.token
  }
}

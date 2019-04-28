package hazzlenut.twitch

import cats.Monad
import cats.implicits._
import hazzlenut.errors.HazzlenutError
import scalaz.zio.{DefaultRuntime, ZIO}
import scalaz.zio.interop.catz._

case class TwitchAppCredentials(clientId: String, clientSecret: String)

case class AccessToken(token: String)

trait OAuth[F[_]] {
  def obtainAccessToken: F[AccessToken]
}

object OAuth{
  def apply[F[_]](implicit F: OAuth[F]) = F
}

object OAuthImplicit {
  implicit val IOOAuth: OAuth[ZIO[TwitchAppCredentials, HazzlenutError, ?]] =
    new OAuth[ZIO[TwitchAppCredentials, HazzlenutError, ?]] {
      override def obtainAccessToken: ZIO[TwitchAppCredentials, HazzlenutError, AccessToken] = {
        ZIO.accessM(credentials => {
          ZIO.effect(AccessToken("token"))
              .foldM(throwable => ZIO.fail(HazzlenutError(throwable)), result => ZIO.succeed(result) )
        })
      }
    }
}

object OAuthProvider {
  def obtainAccessToken[F[_]: OAuth] : F[AccessToken] = OAuth[F].obtainAccessToken
}

object Authenticate{
  import OAuthProvider._

  def run[F[_]: OAuth : Monad] : F[String] = {
    for {
      accessToken <- obtainAccessToken
    } yield accessToken.token
  }
}



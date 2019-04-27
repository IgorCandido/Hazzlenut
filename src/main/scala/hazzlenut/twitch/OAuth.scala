package hazzlenut.twitch

import cats.Monad
import cats.implicits._
import hazzlenut.errors.HazzlenutError
import scalaz.zio.ZIO
import scalaz.zio.interop.catz._

case class TwitchAppCredentials(clientId: String, clientSecrete: String)

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

object Authenticate{
  def run[F[_]] (implicit oauth: OAuth[F], monad: Monad[F]) : F[String] = {
    for {
      accessToken <- OAuth[F].obtainAccessToken
    } yield accessToken.token
  }

}

object TestRun {
  import OAuthImplicit.IOOAuth
  val result = Authenticate.run[ZIO[TwitchAppCredentials, HazzlenutError, ?]]
  result.provide(TwitchAppCredentials("test", "test"))
}



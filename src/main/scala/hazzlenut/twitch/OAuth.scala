package hazzlenut.twitch

import cats.Monad
import cats.implicits._
import cats.data.Reader
import hazzlenut.errors.HazzlenutError
import scalaz.zio.{IO, ZIO}

import scala.concurrent.Future

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
  def run[F[_] : Monad : OAuth] = {
    for {
      accessToken <- OAuth[F].obtainAccessToken
    } yield accessToken.token
  }

}

object TestRun {
  import OAuthImplicit._
  val result = Authenticate.run
  result.provide(TwitchAppCredentials("test", "test"))
}



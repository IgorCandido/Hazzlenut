package hazzlenut.twitch

import java.net.URI

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import akka.stream.Materializer
import cats.Monad
import cats.data.Reader
import cats.implicits._
import hazzlenut.errors.HazzlenutError
import scalaz.zio.{DefaultRuntime, IO, UIO, ZIO}
import scalaz.zio.interop.catz._
import com.github.dakatsuka.akka.http.oauth2.client.{Client, Config, GrantType}
import com.github.dakatsuka.akka.http.oauth2.client.Error.UnauthorizedException
import com.github.dakatsuka.akka.http.oauth2.client.strategy._
import com.github.dakatsuka.akka.http.oauth2.client.{
  AccessToken => DakatSukaAccessToken
}

import scala.concurrent.{ExecutionContext, Future}

case class TwitchAppCredentials(clientId: String, clientSecret: String)

case class AccessToken(token: String)

trait OAuth[F[_]] {
  def getAuthorizeUrl(credential: TwitchAppCredentials)(
    implicit system: ActorSystem,
    ec: ExecutionContext,
    mat: Materializer
  ): Option[String]

  def obtainAccessToken(credential: TwitchAppCredentials)(
    implicit system: ActorSystem,
    ec: ExecutionContext,
    mat: Materializer
  ): F[AccessToken]
}

object OAuth {
  def apply[F[_]](implicit F: OAuth[F]) = F
}

object OAuthImplicit {
  implicit val IOOAuth: OAuth[ZIO[Any, HazzlenutError, ?]] =
    new OAuth[ZIO[Any, HazzlenutError, ?]] {
      override def getAuthorizeUrl(credential: TwitchAppCredentials)(
        implicit system: ActorSystem,
        ec: ExecutionContext,
        mat: Materializer
      ): Option[String] = {
        val config = Config(
          clientId = credential.clientId,
          clientSecret = credential.clientSecret,
          site = URI.create("https://id.twitch.tv"),
          authorizeUrl = "/oauth2/authorize"
        )

        val client = new Client(config)

        val authorizeUrl: Option[Uri] =
          client.getAuthorizeUrl(
            GrantType.AuthorizationCode,
            Map(
              "redirect_uri" -> "http://localhost:8000/login/auth/twitch",
              "scope" -> "channel:read:subscriptions"
            )
          )

        authorizeUrl.map(_.toString())
      }
      override def obtainAccessToken(credential: TwitchAppCredentials)(
        implicit system: ActorSystem,
        ec: ExecutionContext,
        mat: Materializer
      ): ZIO[Any, HazzlenutError, AccessToken] = {
        ZIO
          .fromFuture(ec => {
            implicit val exec = ec
            val config = Config(
              clientId = credential.clientId,
              clientSecret = credential.clientSecret,
              site = URI.create("https://id.twitch.tv:443"),
              authorizeUrl = "/oauth2/authorize"
            )

            val client = new Client(config)

            val accessToken: Future[Either[Throwable, DakatSukaAccessToken]] =
              client
                .getAccessToken(
                  GrantType.AuthorizationCode,
                  Map(
                    "code" -> "yyyyyy",
                    "redirect_uri" -> "http://localhost:8000/login/auth/twitch"
                  )
                )
                .recover {
                  case throwable => Either.left(HazzlenutError(throwable))
                }

            accessToken.map {
              case Right(accessToken) =>
                Either.right(AccessToken(accessToken.accessToken))
              case Left(throwable) => Either.left(throwable)
            }
          })
          .flatMap {
            case Right(result)   => ZIO.succeed(result)
            case Left(throwable) => ZIO.fail(throwable)
          }
          .mapError(throwable => HazzlenutError(throwable))
      }
    }
}

object OAuthProvider {
  def obtainAccessToken[F[_]: OAuth](credential: TwitchAppCredentials)(
    implicit system: ActorSystem,
    ec: ExecutionContext,
    mat: Materializer
  ): F[AccessToken] =
    OAuth[F].obtainAccessToken(credential)
}

object Authenticate {
  import OAuthProvider._

  def run[F[_]: OAuth: Monad](
    implicit system: ActorSystem,
    ec: ExecutionContext,
    mat: Materializer
  ): Reader[TwitchAppCredentials, F[String]] = Reader { credential =>
    for {
      accessToken <- obtainAccessToken(credential)
    } yield accessToken.token
  }
}

package hazzlenut.twitch

import java.net.URI

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import akka.stream.Materializer
import com.github.dakatsuka.akka.http.oauth2.client.{Client, Config, GrantType}
import hazzlenut.errors.HazzlenutError
import com.github.dakatsuka.akka.http.oauth2.client.{
  AccessToken => DakatSukaAccessToken
}
import hazzlenut.handler.AuthenticationHandler
import hazzlenut.services.twitch.{
  AccessToken,
  Authenticate,
  OAuth,
  TwitchAppCredentials
}
import cats.implicits._
import scalaz.zio.interop.catz._
import scalaz.zio.{DefaultRuntime, ZIO}

import scala.concurrent.{ExecutionContext, Future}

object OAuthZIO {
  implicit val IOOAuth: OAuth[ZIO[Any, HazzlenutError, ?]] =
    new OAuth[ZIO[Any, HazzlenutError, ?]] {
      override def getAuthorizeUrl(credential: TwitchAppCredentials)(
        implicit system: ActorSystem,
        ec: ExecutionContext,
        mat: Materializer
      ): ZIO[Any, HazzlenutError, Option[String]] = {
        ZIO.fromFunction { _ =>
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
                "redirect_uri" -> "http://localhost:8000/oauth/reply",
                "scope" -> "channel:read:subscriptions"
              )
            )

          authorizeUrl.map(_.toString())
        }
      }
      override def obtainAccessToken(credential: TwitchAppCredentials,
                                     code: String)(
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
              authorizeUrl = "/oauth2/authorize",
              tokenUrl = "/oauth2/token"
            )

            val client = new Client(config)

            val accessToken: Future[Either[Throwable, DakatSukaAccessToken]] =
              client
                .getAccessToken(
                  GrantType.AuthorizationCode,
                  Map(
                    "code" -> code,
                    "redirect_uri" -> "http://localhost:8000/oauth/reply"
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

  implicit val oAuthZIO = new AuthenticationHandler {
    override def getAuthUrl(implicit system: ActorSystem,
                            ec: ExecutionContext,
                            mat: Materializer): Future[Option[String]] = {
      val runtime = new DefaultRuntime {}
      val getUrl =
        Authenticate.getUrlToAuthenticate[ZIO[Any, HazzlenutError, ?]]

      runtime.unsafeRunToFuture(
        getUrl.run(
          TwitchAppCredentials(
            "n9pbmipzozy0q81rn9g97ht6g61oeq",
            "tm7s99klifle5mi05av8ffupi40x8p"
          )
        )
      )
    }

    override def obtainOAuth(code: String)(
      implicit system: ActorSystem,
      ec: ExecutionContext,
      mat: Materializer
    ): Future[String] = {
      val runtime = new DefaultRuntime {}
      val getOAuthToken =
        Authenticate.authenticate[ZIO[Any, HazzlenutError, ?]]

      runtime.unsafeRunToFuture(getOAuthToken.run((TwitchAppCredentials(
        "n9pbmipzozy0q81rn9g97ht6g61oeq",
        "tm7s99klifle5mi05av8ffupi40x8p"
      ), code)))
    }
  }
}

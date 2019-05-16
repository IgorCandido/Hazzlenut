package hazzlenut.twitch

import java.net.URI

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import akka.stream.Materializer
import cats.implicits._
import com.github.dakatsuka.akka.http.oauth2.client.{
  Client,
  Config,
  GrantType,
  AccessToken => DakatSukaAccessToken
}
import hazzlenut.errors.{HazzlenutError, InvalidConfiguration, ThrowableError}
import hazzlenut.handler.AuthenticationHandler
import hazzlenut.services.twitch.{
  AccessToken,
  Authenticate,
  Configuration,
  OAuth
}
import hazzlenut.util.MapGetterValidation._
import scalaz.zio.interop.catz._
import scalaz.zio.{DefaultRuntime, ZIO}

import scala.concurrent.{ExecutionContext, Future}

object TwitchZIO {
  implicit val configurationZIO =
    new Configuration[ZIO[Any, HazzlenutError, ?]] {
      override def get(): ZIO[Any, HazzlenutError, Configuration.Config] = {
        for {
          configOrValidationError <- ZIO.succeedLazy {
            (
              sys.env.getMandatory("clientId"),
              sys.env.getMandatory("clientSecret"),
              sys.env.getMandatory("redirectUri"),
              sys.env.getMandatory("tokenUrl"),
              sys.env.getMandatory("authorizeUrl"),
              sys.env.getMandatory("siteUrl"),
              sys.env.getMandatory("scopes")
            ).mapN(Configuration.Config.apply)
          }
          validConfig <- configOrValidationError.fold(
            errors =>
              ZIO.fail(
                InvalidConfiguration(
                  errors
                    .map(e => s"${e.name}: ${e.error}")
                    .mkString_("", ",", "")
                )
            ),
            config => ZIO.succeed(config)
          )
        } yield validConfig
      }
    }

  implicit val oAuthZIO: OAuth[ZIO[Any, HazzlenutError, ?]] =
    new OAuth[ZIO[Any, HazzlenutError, ?]] {
      override def getAuthorizeUrl(configuration: Configuration.Config)(
        implicit system: ActorSystem,
        ec: ExecutionContext,
        mat: Materializer
      ): ZIO[Any, HazzlenutError, Option[String]] = {
        ZIO.fromFunction { _ =>
          val config = Config(
            clientId = configuration.clientId,
            clientSecret = configuration.clientSecret,
            site = URI.create(configuration.siteUrl),
            authorizeUrl = configuration.authorizeUrl,
            tokenUrl = configuration.tokenUrl
          )

          val client = new Client(config)

          val authorizeUrl: Option[Uri] =
            client.getAuthorizeUrl(
              GrantType.AuthorizationCode,
              Map(
                "redirect_uri" -> configuration.redirectUri,
                "scope" -> configuration.scopes.mkString("+")
              )
            )

          authorizeUrl.map(_.toString())
        }
      }

      override def obtainAccessToken(code: String,
                                     configuration: Configuration.Config)(
        implicit system: ActorSystem,
        ec: ExecutionContext,
        mat: Materializer
      ): ZIO[Any, HazzlenutError, AccessToken] = {
        ZIO
          .fromFuture(ec => {
            implicit val exec = ec
            val config = Config(
              clientId = configuration.clientId,
              clientSecret = configuration.clientSecret,
              site = URI.create(configuration.siteUrl),
              authorizeUrl = configuration.authorizeUrl,
              tokenUrl = configuration.tokenUrl
            )

            val client = new Client(config)

            val accessToken: Future[Either[Throwable, DakatSukaAccessToken]] =
              client
                .getAccessToken(
                  GrantType.AuthorizationCode,
                  Map(
                    "code" -> code,
                    "redirect_uri" -> configuration.redirectUri
                  )
                )
                .recover {
                  case throwable => Either.left(ThrowableError(throwable))
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
          .mapError(throwable => ThrowableError(throwable))
      }
    }

  implicit val authHandlerZIO = new AuthenticationHandler {

    override def getAuthUrl(implicit system: ActorSystem,
                            ec: ExecutionContext,
                            mat: Materializer): Future[Option[String]] = {
      val runtime = new DefaultRuntime {}
      val getUrl =
        Authenticate.getUrlToAuthenticate[ZIO[Any, HazzlenutError, ?]]

      runtime.unsafeRunToFuture(getUrl)
    }

    override def obtainOAuth(code: String)(
      implicit system: ActorSystem,
      ec: ExecutionContext,
      mat: Materializer
    ): Future[String] = {
      val runtime = new DefaultRuntime {}
      val getOAuthToken =
        Authenticate.authenticate[ZIO[Any, HazzlenutError, ?]]

      runtime.unsafeRunToFuture(getOAuthToken.run(code))
    }
  }
}

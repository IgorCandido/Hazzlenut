package hazzlenut.services.twitch

import java.net.URI

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import akka.stream.Materializer
import com.github.dakatsuka.akka.http.oauth2.client.{Client, Config, GrantType}
import hazzlenut.errors.HazzlenutError
import hazzlenut.errors.HazzlenutError.ThrowableError
import hazzlenut.handler.AuthenticationHandler
import hazzlenut.util.MapGetterValidation._
import scalaz.zio.ZIO
import scalaz.zio.interop.catz._

import scala.concurrent.{ExecutionContext, Future}

object TwitchZIO {
  implicit val configurationZIO =
    new Configuration[ZIO[Any, HazzlenutError, ?]] {

      override def pureAsync(
        f: => ConfigurationValidation[Configuration.Config]
      ): ZIO[Any, HazzlenutError, ConfigurationValidation[
        Configuration.Config
      ]] = ZIO.succeedLazy(f)

      override def getConfig(): (ConfigurationValidation[String],
                                 ConfigurationValidation[String],
                                 ConfigurationValidation[String],
                                 ConfigurationValidation[String],
                                 ConfigurationValidation[String],
                                 ConfigurationValidation[String],
                                 ConfigurationValidation[String]) =
        (
          sys.env.getMandatory("clientId"),
          sys.env.getMandatory("clientSecret"),
          sys.env.getMandatory("redirectUri"),
          sys.env.getMandatory("tokenUrl"),
          sys.env.getMandatory("authorizeUrl"),
          sys.env.getMandatory("siteUrl"),
          sys.env.getMandatory("scopes")
        )
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

            for {
              accessTokenOrError <- client
                .getAccessToken(
                  GrantType.AuthorizationCode,
                  Map(
                    "code" -> code,
                    "redirect_uri" -> configuration.redirectUri
                  )
                )
              accessToken = accessTokenOrError.map(
                access =>
                  AccessToken(
                    accessToken = access.accessToken,
                    tokenType = access.tokenType,
                    expiresIn = access.expiresIn,
                    refreshToken = access.refreshToken
                )
              )
            } yield accessToken
          })
          .flatMap {
            case Right(result)   => ZIO.succeed(result)
            case Left(throwable) => ZIO.fail(throwable)
          }
          .mapError(throwable => ThrowableError(throwable))
      }

      /* For refresh
      'refresh_token={refreshToken}&grant_type=refresh_token' query string parameters
       */
      override def refreshAccessToken(refreshToken: String,
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

            for {
              accessTokenOrError <- client
                .getAccessToken(
                  GrantType.RefreshToken,
                  Map("refresh_token" -> refreshToken)
                )
              accessToken = accessTokenOrError.map(
                access =>
                  AccessToken(
                    accessToken = access.accessToken,
                    tokenType = access.tokenType,
                    expiresIn = access.expiresIn,
                    refreshToken = access.refreshToken
                )
              )
            } yield accessToken
          })
          .flatMap {
            case Right(result)   => ZIO.succeed(result)
            case Left(throwable) => ZIO.fail(throwable)
          }
          .mapError(throwable => ThrowableError(throwable))
      }
    }

  implicit val authHandlerZIO = new AuthenticationHandler {
    import hazzlenut.util.ZIORuntime._
    override def getAuthUrl(implicit system: ActorSystem,
                            ec: ExecutionContext,
                            mat: Materializer): Future[Option[String]] = {
      val getUrl =
        Authenticate.getUrlToAuthenticate[ZIO[Any, HazzlenutError, ?]]

      runtime.unsafeRunToFuture(getUrl)
    }

    override def obtainOAuth(code: String)(
      implicit system: ActorSystem,
      ec: ExecutionContext,
      mat: Materializer
    ): Future[AccessToken] = {
      val getOAuthToken =
        Authenticate.authenticate[ZIO[Any, HazzlenutError, ?]]

      runtime.unsafeRunToFuture(getOAuthToken.run(code))
    }

    override def refreshToken(code: String)(
      implicit system: ActorSystem,
      ec: ExecutionContext,
      mat: Materializer
    ): Future[Either[HazzlenutError, AccessToken]] = {
      val refreshToken =
        Authenticate.refresh[ZIO[Any, HazzlenutError, ?]]

      runtime.unsafeRunToFuture(refreshToken.run(code).either)
    }
  }
}

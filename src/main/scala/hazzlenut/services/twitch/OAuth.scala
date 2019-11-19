package hazzlenut.services.twitch

import java.net.URI

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import akka.stream.Materializer
import com.github.dakatsuka.akka.http.oauth2.client.{Client, GrantType, Config => DakaConfig}
import hazzlenut.errors.HazzlenutError
import hazzlenut.errors.HazzlenutError.ThrowableError
import hazzlenut.services.twitch.Configuration.Config
import zio.ZIO

import scala.concurrent.ExecutionContext

case class TwitchAppCredentials(clientId: String, clientSecret: String)

case class AccessToken(accessToken: String,
                       tokenType: String,
                       expiresIn: Int, // Number of seconds
                       refreshToken: Option[String])

trait OAuth[F[_]] {
  def getAuthorizeUrl(config: Config)(implicit system: ActorSystem,
                                      ec: ExecutionContext,
                                      mat: Materializer): F[Option[String]]

  def obtainAccessToken(code: String, config: Config)(
    implicit system: ActorSystem,
    ec: ExecutionContext,
    mat: Materializer
  ): F[AccessToken]

  def refreshAccessToken(refreshToken: String, config: Config)(
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

    def refreshAccessToken[F[_]: OAuth](refreshToken: String, config: Config)(
      implicit system: ActorSystem,
      ec: ExecutionContext,
      mat: Materializer
    ): F[AccessToken] =
      OAuth[F].refreshAccessToken(refreshToken, config)


    def getClientRedirectUrl[F[_]: OAuth](config: Config)(
      implicit system: ActorSystem,
      ec: ExecutionContext,
      mat: Materializer
    ): F[Option[String]] =
      OAuth[F].getAuthorizeUrl(config)
  }

  implicit val oAuthZIO: OAuth[ZIO[Any, HazzlenutError, ?]] =
    new OAuth[ZIO[Any, HazzlenutError, ?]] {
      override def getAuthorizeUrl(configuration: Configuration.Config)(
        implicit system: ActorSystem,
        ec: ExecutionContext,
        mat: Materializer
      ): ZIO[Any, HazzlenutError, Option[String]] = {
        ZIO.fromFunction { _ =>
          val config = DakaConfig(
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
            val config = DakaConfig(
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
            val config = DakaConfig(
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
}

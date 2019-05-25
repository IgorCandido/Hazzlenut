package hazzlenut.handler

import akka.actor.ActorSystem
import akka.stream.Materializer
import hazzlenut.errors.HazzlenutError
import hazzlenut.services.twitch.AccessToken

import scala.concurrent.{ExecutionContext, Future}

trait AuthenticationHandler {
  def getAuthUrl(implicit system: ActorSystem,
                 ec: ExecutionContext,
                 mat: Materializer): Future[Option[String]]

  def obtainOAuth(code: String)(implicit system: ActorSystem,
                                ec: ExecutionContext,
                                mat: Materializer): Future[AccessToken]

  def refreshToken(code: String)(implicit system: ActorSystem,
                                ec: ExecutionContext,
                                mat: Materializer): Future[Either[HazzlenutError, AccessToken]]
}

object AuthenticationHandler {

  object dsl {
    def getAuthenticationUrl(
      implicit system: ActorSystem,
      ec: ExecutionContext,
      mat: Materializer,
      authentication: AuthenticationHandler
    ): Future[Option[String]] = authentication.getAuthUrl

    def obtainOauthToken(code: String)(
      implicit system: ActorSystem,
      ec: ExecutionContext,
      mat: Materializer,
      authentication: AuthenticationHandler
    ): Future[AccessToken] =
      authentication.obtainOAuth(code)

    def refreshOauthToken(refreshToken: String)(
      implicit system: ActorSystem,
      ec: ExecutionContext,
      mat: Materializer,
      authentication: AuthenticationHandler
    ): Future[Either[HazzlenutError, AccessToken]] =
      authentication.refreshToken(refreshToken)
  }
}

package hazzlenut.handler

import akka.actor.ActorSystem
import akka.stream.Materializer
import hazzlenut.errors.HazzlenutError
import hazzlenut.services.twitch.{AccessToken, Authenticate}
import zio.ZIO
import zio.interop.catz._

import scala.concurrent.{ExecutionContext, Future}

trait AuthenticationHandler {
  def getAuthUrl(implicit system: ActorSystem,
                 ec: ExecutionContext,
                 mat: Materializer): Future[Option[String]]

  def obtainOAuth(code: String)(implicit system: ActorSystem,
                                ec: ExecutionContext,
                                mat: Materializer): Future[AccessToken]

  def refreshToken(code: String)(
    implicit system: ActorSystem,
    ec: ExecutionContext,
    mat: Materializer
  ): Future[Either[HazzlenutError, AccessToken]]

  def reAuthenticate(): Either[HazzlenutError, Unit]
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

    def reAuthenticate(
      implicit authenticationHandler: AuthenticationHandler
    ): Either[HazzlenutError, Unit] =
      authenticationHandler.reAuthenticate()
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

    override def reAuthenticate(): Either[HazzlenutError, Unit] = {
      val reauthentication =
        Authenticate.reAuthenticate[ZIO[Any, HazzlenutError, ?]]

      runtime.unsafeRun(reauthentication)
    }
  }
}

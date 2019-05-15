package hazzlenut.handler

import akka.actor.ActorSystem
import akka.stream.Materializer

import scala.concurrent.{ExecutionContext, Future}

trait AuthenticationHandler {
  def getAuthUrl(implicit system: ActorSystem,
                 ec: ExecutionContext,
                 mat: Materializer): Future[Option[String]]

  def obtainOAuth(code: String)(implicit system: ActorSystem,
                                ec: ExecutionContext,
                                mat: Materializer): Future[String]
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
    ): Future[String] =
      authentication.obtainOAuth(code)
  }
}

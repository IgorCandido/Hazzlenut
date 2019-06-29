package hazzlenut.api

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.util.Timeout
import hazzlenut.errors.HazzlenutError
import hazzlenut.handler.{AuthenticationHandler, TwitchClientHandler}
import hazzlenut.handler.TwitchClientHandler.dsl._
import hazzlenut.services.twitch.TokenGuardian.Authenticated
import hazzlenut.services.twitch.TokenHolder.{AskAccessToken, ReplyAccessToken}
import hazzlenut.services.twitch.TwitchClient
import hazzlenut.util.HttpClient
import scalaz.zio.ZIO

import scala.concurrent.duration._

object Authentication {

  def publicRoute(implicit tokenGuardian: ActorRef,
                  actorSystem: ActorSystem) = {
    route[ZIO[Any, HazzlenutError, ?]]
  }

  def route[F[_]: TwitchClientHandler: TwitchClient: HttpClient](
    implicit a: AuthenticationHandler,
    tokenGuardian: ActorRef
  ) = {
    import AuthenticationHandler.dsl._
    get {
      pathPrefix("oauth") {
        path("login") {
          (extractExecutionContext & extractActorSystem & extractMaterializer) {
            (context, system, materializer) =>
              implicit val c = context
              implicit val s = system
              implicit val m = materializer
              onSuccess(getAuthenticationUrl) { urlMaybe =>
                urlMaybe.fold(redirect(Uri("/error"), StatusCodes.Found)) {
                  url =>
                    redirect(Uri(url), StatusCodes.Found)
                }
              }
          }
        } ~
          path("reply") {
            parameter('code.as[String]) { code =>
              (extractExecutionContext & extractActorSystem & extractMaterializer) {
                (context, system, materializer) =>
                  {
                    implicit val c = context
                    implicit val s = system
                    implicit val m = materializer
                    onSuccess(obtainOauthToken(code)) { token =>
                      tokenGuardian ! Authenticated(token)
                      complete(s"Token obtained: ${token}")
                    }
                  }
              }
            }

          } ~
          path("refresh") {
            parameter('refresh.as[String]) { refreshToken =>
              (extractExecutionContext & extractActorSystem & extractMaterializer) {
                (context, system, materializer) =>
                  {
                    implicit val c = context
                    implicit val s = system
                    implicit val m = materializer
                    onSuccess(refreshOauthToken(refreshToken)) { token =>
                      complete(s"Token refreshed: ${token}")
                    }

                  }
              }
            }
          } ~
          path("user") {
            (extractExecutionContext & extractActorSystem & extractMaterializer) {
              (context, system, materializer) =>
                {
                  implicit val c = context
                  implicit val s = system
                  implicit val m = materializer
                  implicit val timeout = Timeout(5 seconds)

                  onSuccess(for {
                    token <- (tokenGuardian ? AskAccessToken)
                      .mapTo[ReplyAccessToken]
                    user <- retrieveUser(token.accessToken)
                  } yield user) { u =>
                    complete(s"user ${u}")
                  }
                }
            }
          }
      } ~
        path("error") {
          complete(StatusCodes.InternalServerError, "Failure")
        }
    }
  }
}

package hazzlenut.api

import akka.actor.ActorRef
import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.util.Timeout
import hazzlenut.errors.HazzlenutError
import hazzlenut.handler.AuthenticationHandler
import hazzlenut.services.twitch.TokenGuardian.Authenticated
import hazzlenut.services.twitch.TokenHolder.{AskAccessToken, ReplyAccessToken}
import hazzlenut.services.twitch.TwitchClient.dsl.retrieveUser
import hazzlenut.util.ZIORuntime.runtime
import scalaz.zio.ZIO
import scalaz.zio.interop.catz._

import scala.concurrent.duration._

object Authentication {

  def publicRoute(implicit tokenGuardian: ActorRef) = {
    route
  }

  def route(implicit a: AuthenticationHandler, tokenGuardian: ActorRef) = {
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
                    user <- runtime.unsafeRunToFuture(
                      retrieveUser[ZIO[Any, HazzlenutError, ?]](
                        token.accessToken
                      )
                    )
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

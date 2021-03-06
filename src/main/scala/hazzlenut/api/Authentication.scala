package hazzlenut.api

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import hazzlenut.errors.HazzlenutError
import hazzlenut.handler.{AuthenticationHandler, TwitchClientHandler}
import hazzlenut.handler.TwitchClientHandler.dsl._
import hazzlenut.services.twitch.TokenGuardian.Authenticated
import hazzlenut.services.twitch.TokenHolder.{AskAccessToken, ReplyAccessToken}
import hazzlenut.services.twitch.{CommonReferences, TwitchClient}
import hazzlenut.services.twitch.UserInfo.{ProvideUser, RetrieveUser}
import hazzlenut.util.{HttpClient, UnmarshallerEntiy}
import zio.ZIO

import scala.concurrent.duration._

object Authentication {

  def publicRoute(tokenGuardian: ActorRef,
                  userInfo: ActorRef)(implicit actorSystem: ActorSystem): Route = {
    route[ZIO[Any, HazzlenutError, ?]](tokenGuardian, userInfo)
  }

  def route[F[_]: TwitchClientHandler: TwitchClient: HttpClient: UnmarshallerEntiy](
    tokenGuardian: ActorRef,
    userInfo: ActorRef
  )(implicit a: AuthenticationHandler): Route = {
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
          } ~
          path("followers") {
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
                    user <- (userInfo ? RetrieveUser)
                      .mapTo[ProvideUser]
                    followers <- retrieveFollowers(token.accessToken, user.user.id, None)
                  } yield followers) { f =>
                    complete(s"followers ${f}")
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

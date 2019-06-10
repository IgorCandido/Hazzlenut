package hazzlenut.api

import akka.actor.ActorRef
import akka.pattern.ask
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.model.{HttpRequest, StatusCodes, Uri}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.util.Timeout
import hazzlenut.handler.AuthenticationHandler
import hazzlenut.services.twitch.TokenGuardian.Authenticated
import hazzlenut.services.twitch.TokenHolder.{AskAccessToken, ReplyAccessToken}
import hazzlenut.services.twitch.model.{TwitchReply, User}

import concurrent.duration._

object Authentication {

  def publicRoute(implicit tokenGuardian: ActorRef) = {
    import hazzlenut.services.twitch.TwitchZIO._
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

                  onSuccess(
                    (tokenGuardian ? AskAccessToken).mapTo[ReplyAccessToken]
                  ) { token =>
                    onSuccess(
                      Http().singleRequest(
                        HttpRequest(uri = "https://api.twitch.tv/helix/users")
                          .addCredentials(
                            OAuth2BearerToken(token.accessToken.accessToken)
                          )
                      )
                    ) { user =>
                      onSuccess(Unmarshal(user.entity).to[TwitchReply[User]]) {
                        userInfo =>
                          complete(s"user ${userInfo.data.head}")
                      }

                    }
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

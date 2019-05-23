package hazzlenut.api

import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.server.Directives._
import hazzlenut.handler.AuthenticationHandler

object Authentication {
  val publicRoute = {
    import hazzlenut.twitch.TwitchZIO._
    route
  }

  def route(implicit a: AuthenticationHandler) = {
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
                      complete(s"Token obtained: ${token}")
                    }

                  }
              }
            }

          } ~
        path("refresh") {
          parameter('refresh.as[String]) {refreshToken =>
            (extractExecutionContext & extractActorSystem & extractMaterializer){
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
        }
      } ~
        path("error") {
          complete(StatusCodes.InternalServerError, "Failure")
        }
    }
  }
}

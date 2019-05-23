package hazzlenut.services.twitch

import akka.actor.Actor
import akka.stream.ActorMaterializer
import hazzlenut.errors.HazzlenutError
import hazzlenut.util.ZIORuntime.runtime
import scalaz.zio.ZIO
import scalaz.zio.interop.catz._

import akka.pattern.pipe

case object ProvideAccessToken
case object TokenExpiredNeedNew

class TokenHolder(var accessToken: AccessToken) extends Actor {
  import hazzlenut.services.twitch.TwitchZIO._
  implicit val system = context.system
  implicit val materializer = ActorMaterializer() // Alright to create new materializer roughly 14 ms creation
  implicit val executionContext = system.dispatcher // TODO Look into if it is problematic to use akka EC for http requests


  override def receive: Receive = {
    case ProvideAccessToken =>
      sender() ! accessToken

    case TokenExpiredNeedNew =>
      val refreshOAuthToken = Authenticate
        .refresh[ZIO[Any, HazzlenutError, ?]]
        .run(accessToken.refreshToken)
      // .either <- Shifts errors to either instead
      // .catchAll() <- Catch all errors and maybe return either instead of A

      // Record list of requests whilst waiting for the reply

      runtime.unsafeRunToFuture(refreshOAuthToken) pipeTo self

    case Right(accessTokenResult: AccessToken) =>
      accessToken = accessTokenResult

    // Send new access token to interested parties (actors)

    case Left(error: HazzlenutError) =>
    // Handle error refresh token, maybe retry? Maybe do this in ZIO even before piping out
    // Ultimate scenario token not refreshable inform that user needs to be pushed to oauth again

  }
}

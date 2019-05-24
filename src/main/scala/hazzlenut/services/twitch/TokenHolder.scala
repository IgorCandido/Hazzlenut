package hazzlenut.services.twitch

import akka.actor.{Actor, ActorRef}
import akka.stream.ActorMaterializer
import hazzlenut.errors.HazzlenutError
import hazzlenut.util.ZIORuntime.runtime
import scalaz.zio.ZIO
import scalaz.zio.interop.catz._
import akka.pattern.pipe

case object ProvideAccessToken
case object TokenExpiredNeedNew
case class RefreshedAccessToken(accessTokenOrError: Either[HazzlenutError, AccessToken])

/*
 hold oauth token,
 refresh oauth token,
 report problem on refreshing and advice that user goes through into Oauth flow again
 */
class TokenHolder(accessToken: AccessToken) extends Actor {
  import hazzlenut.services.twitch.TwitchZIO._
  implicit val system = context.system
  implicit val materializer = ActorMaterializer() // Alright to create new materializer roughly 14 ms creation
  implicit val executionContext = system.dispatcher // TODO Look into if it is problematic to use akka EC for http requests

  // Start with access token provided
  context.become(hasAccessToken(accessToken))

  override def receive: Receive = ???

  def hasAccessToken(accessToken: AccessToken) : Receive = {
    case ProvideAccessToken =>
      sender() ! accessToken

    case TokenExpiredNeedNew =>
      val refreshOAuthToken = Authenticate
        .refresh[ZIO[Any, HazzlenutError, ?]]
        .run(accessToken.refreshToken)
        .either // <- Shifts errors to either instead

      // Record list of requests whilst waiting for the reply
      context.become(waitingForRefreshedToken(Set(sender())))

      runtime.unsafeRunToFuture(refreshOAuthToken).map(RefreshedAccessToken) pipeTo self
  }

  def waitingForRefreshedToken(interestedActors: Set[ActorRef]): Receive = {
    case ProvideAccessToken | TokenExpiredNeedNew =>
      context.become(waitingForRefreshedToken(interestedActors + sender()))

    case RefreshedAccessToken(Right(accessTokenResult: AccessToken))=>
      // Send new access token to interested parties (actors)
      interestedActors.foreach(_ ! accessTokenResult)

      context.become(hasAccessToken(accessTokenResult))

    case RefreshedAccessToken(Left(error: HazzlenutError)) =>
    // Handle error refresh token, maybe retry? Maybe do this in ZIO even before piping out
    // Ultimate scenario token not refreshable inform that user needs to be pushed to oauth again
    // Maybe kill self actor considering there is not access token or refresh process going on
  }
}

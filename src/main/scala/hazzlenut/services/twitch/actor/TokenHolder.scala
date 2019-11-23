package hazzlenut.services.twitch.actor

import akka.actor.{Actor, ActorRef, Props}
import akka.pattern.pipe
import akka.stream.ActorMaterializer
import cats.Monad
import hazzlenut.errors.HazzlenutError
import hazzlenut.handler.AuthenticationHandler
import hazzlenut.services.twitch.actor.TokenGuardian.Message.CantRenewToken
import hazzlenut.services.twitch.actor.helper.Executor
import hazzlenut.services.twitch.adapters.AccessToken
import hazzlenut.util.LogProvider
import log.effect.LogLevels.Debug
import cats.implicits._
import hazzlenut.services.twitch.actor.helper.Executor.dsl._

object TokenHolder {
  val Name = "TokenHolder"

  def props[F[_]: Monad: LogProvider: Executor](accessToken: AccessToken, tokenGuardian: ActorRef)(
    implicit authenticationHandler: AuthenticationHandler
  ) = Props(new TokenHolder[F](accessToken, tokenGuardian))

  case object AskAccessToken
  case class ReplyAccessToken(accessToken: AccessToken)
  case object TokenExpiredNeedNew
  case class RefreshedAccessToken(
    accessTokenOrError: Either[HazzlenutError, AccessToken]
  )
}

/*
 hold oauth token,
 refresh oauth token,
 report problem on refreshing and advice that user goes through into Oauth flow again
 */
class TokenHolder[F[_]: Monad: LogProvider: Executor](accessToken: AccessToken, tokenGuardian: ActorRef)(
  implicit authenticationHandler: AuthenticationHandler
) extends Actor {
  import AuthenticationHandler.dsl._
  import TokenHolder._

  implicit val system = context.system
  implicit val materializer = ActorMaterializer() // Alright to create new materializer roughly 14 ms creation
  implicit val executionContext = system.dispatcher // TODO Look into if it is problematic to use akka EC for http requests

  def notAbleToRefresh = {
    tokenGuardian ! CantRenewToken
  }

  // Start with access token provided
  context.become(hasAccessToken(accessToken))

  override def receive: Receive = ???

  def hasAccessToken(accessToken: AccessToken): Receive = {
    case AskAccessToken =>
      sender() ! ReplyAccessToken(accessToken)

    case TokenExpiredNeedNew =>
      accessToken.refreshToken.fold(notAbleToRefresh) { refresh =>
        val refreshToken = refreshOauthToken(refresh)

        // Record list of requests whilst waiting for the reply
        context.become(waitingForRefreshedToken(Set(sender())))

        refreshToken.map(RefreshedAccessToken) pipeTo self
      }
  }

  def waitingForRefreshedToken(interestedActors: Set[ActorRef]): Receive = {
    case AskAccessToken | TokenExpiredNeedNew =>
      context.become(waitingForRefreshedToken(interestedActors + sender()))

    case RefreshedAccessToken(Right(accessTokenResult: AccessToken)) =>
      // Send new access token to interested parties (actors)
      val replyAccessToken = ReplyAccessToken(accessTokenResult)
      interestedActors.foreach(_ ! replyAccessToken)

      context.become(hasAccessToken(accessTokenResult))

    case RefreshedAccessToken(Left(error: HazzlenutError)) =>
      // Handle error refresh token, maybe retry? Maybe do this in ZIO even before piping out
      // Ultimate scenario token not refreshable inform that user needs to be pushed to oauth again
      // Maybe kill self actor considering there is not access token or refresh process going on
      LogProvider.log[F](TokenHolder.Name, Debug, "Unable to refresh access token").map{
        _ => notAbleToRefresh
      }.unsafeRun

  }
}

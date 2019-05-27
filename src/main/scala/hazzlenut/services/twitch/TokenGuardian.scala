package hazzlenut.services.twitch

import akka.actor.{Actor, ActorRef, PoisonPill, Props}
import hazzlenut.errors.HazzlenutError
import hazzlenut.handler.AuthenticationHandler
import hazzlenut.services.twitch.TokenGuardian.{Authenticated, CantRenewToken}

object TokenGuardian {
  case object CantRenewToken
  case class Authenticated(accessToken: AccessToken)

  def props(implicit authenticationHandler: AuthenticationHandler,
            tokenHolderInitializer: TokenHolderInitializer) =
    Props(new TokenGuardian())
}

class TokenGuardian(implicit authenticationHandler: AuthenticationHandler,
                    tokenHolderInitializer: TokenHolderInitializer)
    extends Actor {

  def authenticateUserAgainAndWaitForResult(): Either[HazzlenutError, Unit] = {
    // Asking for authentication
    context.become(waitingForUserAuthentication)
    authenticationHandler.reAuthenticate()
  }

  authenticateUserAgainAndWaitForResult()

  override def receive: Receive = ???

  def workingNormally(tokenHolder: ActorRef): Receive = {
    case CantRenewToken =>
      authenticateUserAgainAndWaitForResult()

      // Kill TokenHolder
      tokenHolder ! PoisonPill

    case msg @ (TokenHolder.TokenExpiredNeedNew | TokenHolder.AskAccessToken) =>
      tokenHolder forward msg
  }

  def waitingForUserAuthentication: Receive = {
    case Authenticated(accessToken) =>
      context.become(
        workingNormally(
          tokenHolderInitializer.initializeTokenHolder(accessToken, self)
        )
      )

    // TODO handle token requests and save them for replies after we initialize the token handler
  }
}

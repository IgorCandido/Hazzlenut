package hazzlenut.services.twitch

import akka.actor.{Actor, ActorRef, PoisonPill, Props}
import hazzlenut.errors.HazzlenutError
import hazzlenut.handler.AuthenticationHandler
import hazzlenut.services.twitch.TokenGuardian.{ApplicationStarted, Authenticated, CantRenewToken}

object TokenGuardian {
  case object CantRenewToken
  case class Authenticated(accessToken: AccessToken)
  case object ApplicationStarted

  def props(implicit authenticationHandler: AuthenticationHandler,
            tokenHolderInitializer: TokenHolderInitializer) =
    Props(new TokenGuardian())
}

class TokenGuardian(implicit authenticationHandler: AuthenticationHandler,
                    tokenHolderInitializer: TokenHolderInitializer)
    extends Actor {

  def workingNormally(tokenHolder: ActorRef): Receive = {
    case CantRenewToken =>
      authenticateUserAgainAndWaitForResult()

      // Kill TokenHolder
      tokenHolder ! PoisonPill

    case msg @ (TokenHolder.TokenExpiredNeedNew | TokenHolder.AskAccessToken) =>
      tokenHolder forward msg
  }

  def waitingForUserAuthentication(
    queuedMessages: Set[(ActorRef, Any)] = Set.empty
  ): Receive = {
    case Authenticated(accessToken) =>
      val tokenHolder =
        tokenHolderInitializer.initializeTokenHolder(accessToken, self)
      // Notify new Token Holder the messages received whilst waiting
      queuedMessages.foreach {
        case (sender, msg) => tokenHolder.tell(msg, sender)
      }
      context.become(workingNormally(tokenHolder))

    case msg @ TokenHolder.AskAccessToken =>
      context.become(
        waitingForUserAuthentication(queuedMessages + ((sender(), msg)))
      )

    case TokenHolder.TokenExpiredNeedNew =>
    // NOP we are already handling the renewal of the oauth
  }

  def authenticateUserAgainAndWaitForResult(): Either[HazzlenutError, Unit] = {
    // Asking for authentication
    context.become(waitingForUserAuthentication())
    authenticationHandler.reAuthenticate()
  }

  override def receive: Receive = {
    case ApplicationStarted =>
      authenticateUserAgainAndWaitForResult()
  }
}

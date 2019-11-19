package hazzlenut.services.twitch.actor

import akka.actor.{Actor, ActorRef, PoisonPill, Props}
import cats.Monad
import hazzlenut.errors.HazzlenutError
import hazzlenut.handler.{AuthenticationHandler, TwitchClientHandler}
import hazzlenut.services.twitch.actor.TokenGuardian.{ApplicationStarted, Authenticated, CantRenewToken}
import hazzlenut.services.twitch.actor.adapter.TwitchClient
import hazzlenut.services.twitch.adapters.AccessToken
import hazzlenut.services.twitch.actor.helper.{TokenHolderInitializer, UserInfoInitializer}
import hazzlenut.util.{HttpClient, LogProvider}

object TokenGuardian {
  case object CantRenewToken
  case class Authenticated(accessToken: AccessToken)
  case object ApplicationStarted

  def props[F[_]: UserInfoInitializer: TwitchClientHandler: TwitchClient: HttpClient: LogProvider: Monad](
    implicit authenticationHandler: AuthenticationHandler,
    tokenHolderInitializer: TokenHolderInitializer,
  ) =
    Props(new TokenGuardian())
}

class TokenGuardian[F[_]: TwitchClientHandler: TwitchClient: HttpClient: LogProvider](
  implicit authenticationHandler: AuthenticationHandler,
  tokenHolderInitializer: TokenHolderInitializer,
  userInfoInitializer: UserInfoInitializer[F],
  monad: Monad[F]
) extends Actor {

  def workingNormally(tokenHolder: ActorRef, userInfo: ActorRef): Receive = {
    case CantRenewToken =>
      authenticateUserAgainAndWaitForResult()

      // Kill TokenHolder
      tokenHolder ! PoisonPill
      // Kill UserInfo
      userInfo ! PoisonPill

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
      val userInfo = userInfoInitializer.initializeUserInfo(tokenHolder)
      context.become(workingNormally(tokenHolder, userInfo))

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

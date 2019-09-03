package hazzlenut.services.twitch

import akka.actor.Actor.Receive
import akka.actor.{ActorContext, ActorRef}
import hazzlenut.services.twitch.TokenHolder.{
  AskAccessToken,
  TokenExpiredNeedNew
}

final case object TokenHolderApi {

  def fetchAccessToken(
    waitingForToken: Receive,
    tokenHolder: ActorRef,
    expiredAccessToken: Boolean = false
  )(implicit context: ActorContext) = {
    context.become(waitingForToken)
    tokenHolder ! (expiredAccessToken match {
      case true  => TokenExpiredNeedNew
      case false => AskAccessToken
    })
  }

}

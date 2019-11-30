package hazzlenut.services.twitch.actor.adapter

import akka.actor.Actor.Receive
import akka.actor.{ActorContext, ActorRef}
import hazzlenut.services.twitch.actor.TokenHolder.{
  AskAccessToken,
  TokenExpiredNeedNew
}

final case object TokenHolderApi {

  def fetchAccessToken(
    waitingForToken: Receive,
    tokenHolder: ActorRef,
    sender: ActorRef,
    expiredAccessToken: Boolean = false
  )(implicit context: ActorContext) = {
    context.become(waitingForToken)
    tokenHolder.tell(expiredAccessToken match {
      case true  => TokenExpiredNeedNew
      case false => AskAccessToken
    }, sender)
  }

}

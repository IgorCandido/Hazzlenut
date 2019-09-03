package hazzlenut.services.twitch

import akka.actor.{Actor, ActorRef, Props}
import hazzlenut.handler.TwitchClientHandler.dsl.retrieveUser
import hazzlenut.services.twitch.TokenHolder.ReplyAccessToken

object Followers {
  def props(tokenHolder: ActorRef, userInfo: ActorRef): Props =
    Props(new Followers(tokenHolder, userInfo))
}

class Followers(tokenHolder: ActorRef, userInfo: ActorRef) extends Actor {
  import TokenHolderApi._

  override def receive: Receive = Actor.emptyBehavior

  def fetchToken(expiredAccessToken: Boolean = false) =
    fetchAccessToken(waitingForToken, tokenHolder, expiredAccessToken)

  def waitingForToken: Receive = {
    case ReplyAccessToken(accessToken) =>
      // Fetch the user from the UserInfo
  }
}

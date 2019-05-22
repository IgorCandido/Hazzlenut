package hazzlenut.services.twitch

import akka.actor.Actor

class TokenHolder(var accessToken: AccessToken) extends Actor{

  override def receive: Receive = {
    case _ =>
  }
}

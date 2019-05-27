package hazzlenut.services.twitch

import akka.actor.{ActorContext, ActorRef}
import hazzlenut.handler.AuthenticationHandler

trait TokenHolderInitializer {
  def initializeTokenHolder(accessToken: AccessToken, self: ActorRef)(
    implicit context: ActorContext,
    authenticationHandler: AuthenticationHandler
  ): ActorRef
}

package hazzlenut.services.twitch

import akka.actor.{ActorContext, ActorRef}
import hazzlenut.handler.AuthenticationHandler

trait TokenHolderInitializer {
  def initializeTokenHolder(accessToken: AccessToken, self: ActorRef)(
    implicit context: ActorContext,
    authenticationHandler: AuthenticationHandler
  ): ActorRef
}

object TokenHolderInitializer {
  implicit val akkaTokenHolderInitializer = new TokenHolderInitializer {
    override def initializeTokenHolder(accessToken: AccessToken,
                                       tokenGuardian: ActorRef)(
      implicit context: ActorContext,
      authenticationHandler: AuthenticationHandler
    ): ActorRef =
      context.system.actorOf(TokenHolder.props(accessToken, tokenGuardian))
  }
}

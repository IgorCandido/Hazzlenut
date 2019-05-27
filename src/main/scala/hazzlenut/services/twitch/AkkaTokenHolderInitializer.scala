package hazzlenut.services.twitch
import akka.actor.{ActorContext, ActorRef}
import hazzlenut.handler.AuthenticationHandler

class AkkaTokenHolderInitializer extends TokenHolderInitializer {
  override def initializeTokenHolder(accessToken: AccessToken, self: ActorRef)(
    implicit context: ActorContext,
    authenticationHandler: AuthenticationHandler
  ): ActorRef =
    context.system.actorOf(TokenHolder.props(accessToken, self))
}

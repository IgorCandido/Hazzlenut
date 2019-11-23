package hazzlenut.services.twitch.actor.helper

import akka.actor.{ActorContext, ActorRef}
import cats.Monad
import hazzlenut.handler.AuthenticationHandler
import hazzlenut.services.twitch.actor.TokenHolder
import hazzlenut.services.twitch.adapters.AccessToken
import hazzlenut.util.LogProvider

trait TokenHolderInitializer[F[_]] {
  def initializeTokenHolder(accessToken: AccessToken, self: ActorRef)(
    implicit context: ActorContext,
    authenticationHandler: AuthenticationHandler
  ): ActorRef
}

object TokenHolderInitializer {
  implicit def akkaTokenHolderInitializer[F[_]: Monad: LogProvider: Executor] = new TokenHolderInitializer[F] {
    override def initializeTokenHolder(accessToken: AccessToken,
                                       tokenGuardian: ActorRef)(
      implicit context: ActorContext,
      authenticationHandler: AuthenticationHandler
    ): ActorRef =
      context.system.actorOf(TokenHolder.props[F](accessToken, tokenGuardian))
  }
}

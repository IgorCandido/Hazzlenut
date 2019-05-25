package hazzlenut.services.twitch

import akka.actor.{Actor, ActorRef}
import hazzlenut.handler.AuthenticationHandler
import hazzlenut.services.twitch.TokenGuardian.{Authenticated, CantRenewToken}

object TokenGuardian {
  case object CantRenewToken
  case class Authenticated(accessToken: AccessToken)
}

class TokenGuardian(implicit authenticationHandler: AuthenticationHandler)
    extends Actor {

  def reAuthenticateUser(): Unit = {
    import java.awt.Desktop;
    import java.net.URI;

    if (Desktop.isDesktopSupported() && Desktop
          .getDesktop()
          .isSupported(Desktop.Action.BROWSE)) {
      Desktop.getDesktop().browse(new URI("http://localhost:8000/oauth/login"));
    }
  }

  // Asking for authentication
  context.become(waitingForUserAuthentication)
  reAuthenticateUser()

  override def receive: Receive = ???

  def workingNormally(tokenHolder: ActorRef): Receive = {
    case CantRenewToken =>
      context.become(waitingForUserAuthentication)
      reAuthenticateUser()
      // TODO find the best way to kill the TokenHolder keeping its mailbox, maybe using KILL and supervisor
      // https://stackoverflow.com/questions/13847963/akka-kill-vs-stop-vs-poison-pill

    // TODO Route the requests to the tokenHolder
  }

  def waitingForUserAuthentication: Receive = {
    case Authenticated(accessToken) =>
      context.become(workingNormally(initializeTokenHolder(accessToken)))

    // TODO handle token requests and save them for replies after we initialize the token handler
  }

  def initializeTokenHolder(accessToken: AccessToken) =
    context.system.actorOf(TokenHolder.props(accessToken, self))
}

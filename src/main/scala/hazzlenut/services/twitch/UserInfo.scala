package hazzlenut.services.twitch

import akka.actor.{Actor, ActorRef, Props, Status}
import akka.pattern.pipe
import akka.stream.ActorMaterializer
import cats.Monad
import cats.implicits._
import hazzlenut.errors.HazzlenutError.UnableToAuthenticate
import hazzlenut.handler.TwitchClientHandler
import hazzlenut.handler.TwitchClientHandler.dsl._
import hazzlenut.services.twitch.TokenHolder.{
  AskAccessToken,
  ReplyAccessToken,
  TokenExpiredNeedNew
}
import hazzlenut.services.twitch.UserInfo.{ProvideUser, RetrieveUser}
import hazzlenut.services.twitch.model.User
import hazzlenut.util.{HttpClient, LogProvider}
import log.effect.LogLevels.Debug
import hazzlenut.util.ShowUtils._

object UserInfo {
  def props[F[_]: TwitchClientHandler: TwitchClient: HttpClient: LogProvider: Monad](
    tokenHolder: ActorRef
  ): Props = Props(new UserInfo(tokenHolder))

  case object RetrieveUser
  case class ProvideUser(user: User)
}

// Killed when TokenHolder is killed in order to reAuthenticated
// Created as soon as the Token is retrieved for a TokenHolder
class UserInfo[F[_]: TwitchClientHandler: TwitchClient: HttpClient: Monad](
  tokenHolder: ActorRef
)(implicit logprovider: LogProvider[F])
    extends Actor {
  implicit val system = context.system
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  fetchAccessToken()

  override def receive: Receive = Actor.emptyBehavior

  def providingUser(user: User): Receive = {
    case RetrieveUser =>
      sender ! ProvideUser(user)
  }

  def waitingForToken: Receive = {
    case ReplyAccessToken(accessToken) =>
      context.become(waitingForUser)
      retrieveUser(accessToken) pipeTo self
  }

  def waitingForUser: Receive = {
    case user: User                                   => context.become(providingUser(user))
    case Status.Failure(_: UnableToAuthenticate.type) => // What to do when failure on getting User
      // 3 - ReAuthenticate
      fetchAccessToken(expiredAccessToken = true)
    case Status.Failure(error) =>
      // 1 - Retry
      for {
        logger <- logprovider.getLoggerByName("UserInfo")
        _ <- logger.write(
          Debug,
          show"Failure on call to get User with error $error"
        )
      } yield
        fetchAccessToken() // Failed to Get User to get access token and try again
    // 2 - Close app (Scenario not necessary for now)
  }

  def fetchAccessToken(expiredAccessToken: Boolean = false) = {
    context.become(waitingForToken)
    tokenHolder ! (expiredAccessToken match {
      case true  => TokenExpiredNeedNew
      case false => AskAccessToken
    })
  }
}

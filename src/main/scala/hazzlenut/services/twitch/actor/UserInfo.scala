package hazzlenut.services.twitch.actor

import akka.actor.{Actor, ActorRef, Props, Status}
import akka.pattern.pipe
import akka.stream.ActorMaterializer
import cats.Monad
import cats.implicits._
import hazzlenut.errors.HazzlenutError.UnableToAuthenticate
import hazzlenut.handler.TwitchClientHandler
import hazzlenut.handler.TwitchClientHandler.dsl._
import hazzlenut.services.twitch.actor.TokenGuardian.ApplicationStarted
import hazzlenut.services.twitch.actor.TokenHolder.ReplyAccessToken
import hazzlenut.services.twitch.actor.UserInfo.{ProvideUser, RetrieveUser}
import hazzlenut.services.twitch.model.User
import hazzlenut.services.twitch.actor.adapter.{TokenHolderApi, TwitchClient}
import hazzlenut.services.twitch.actor.helper.Executor
import hazzlenut.util.ShowUtils._
import hazzlenut.util.{HttpClient, LogProvider, UnmarshallerEntiy}
import log.effect.LogLevels.Debug
import hazzlenut.services.twitch.actor.helper.Executor.dsl._

object UserInfo {
  def props[F[_]: TwitchClientHandler: TwitchClient: HttpClient: LogProvider: Monad: UnmarshallerEntiy: Executor](
    tokenGuardian: ActorRef
  ): Props = Props(new UserInfo(tokenGuardian))

  case object RetrieveUser
  case class ProvideUser(user: User)
}

// Killed when TokenHolder is killed in order to reAuthenticated
// Created as soon as the Token is retrieved for a TokenHolder
class UserInfo[F[_]: TwitchClientHandler: TwitchClient: HttpClient: Monad: UnmarshallerEntiy: Executor](
  tokenHolder: ActorRef
)(implicit logprovider: LogProvider[F])
    extends Actor {
  import TokenHolderApi._
  implicit val system = context.system
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  def fetchToken(expiredAccessToken: Boolean = false) =
    fetchAccessToken(waitingForToken, tokenHolder, self, expiredAccessToken)

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
      fetchToken(expiredAccessToken = true)
    case Status.Failure(error) => {
      // 1 - Retry
      (for {
        logger <- logprovider.getLoggerByName("UserInfo")
        _ <- logger.write(
          Debug,
          show"Failure on call to get User with error $error"
        )
      } yield
        fetchToken()).runToCompletion // Failed to Get User to get access token and try again
      // 2 - Close app (Scenario not necessary for now)
    }
  }

  override def receive: Receive = {
    case ApplicationStarted =>
      fetchToken()
  }
}
package hazzlenut.services.twitch.actor

import akka.actor.{Actor, ActorRef, Props, Status}
import akka.pattern.pipe
import akka.stream.ActorMaterializer
import cats.Monad
import hazzlenut.errors.HazzlenutError.UnableToAuthenticate
import hazzlenut.handler.TwitchClientHandler
import hazzlenut.handler.TwitchClientHandler.dsl._
import hazzlenut.services.twitch.actor.TokenHolder.ReplyAccessToken
import hazzlenut.services.twitch.actor.UserInfo.{ProvideUser, RetrieveUser}
import hazzlenut.services.twitch.model.User
import hazzlenut.services.twitch.actor.adapter.{TokenHolderApi, TwitchClient}
import hazzlenut.services.twitch.actor.helper.Executor
import hazzlenut.util.ShowUtils._
import hazzlenut.util.{HttpClient, LogProvider, UnmarshallerEntiy}
import log.effect.LogLevels.Debug
import hazzlenut.services.twitch.actor.helper.Executor.dsl._
import cats.implicits._
import hazzlenut.services.twitch.actor.model.CommonMessages
import hazzlenut.services.twitch.actor.model.CommonMessages.KillService
import hazzlenut.services.twitch.actor.model.CommonMessages.SupervisorThrowables.ProperlyKilled

object UserInfo {
  val Name = "UserInfo"

  def props[F[_]: TwitchClientHandler: TwitchClient: HttpClient: LogProvider: Monad: UnmarshallerEntiy: Executor](
    tokenHolder: ActorRef
  ): Props = Props(new UserInfo(tokenHolder))

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

  def handleKillService: Receive = {
    case KillService =>
      throw ProperlyKilled
  }

  def withDefaultHandling(receiveHandler: Receive): Receive =
    receiveHandler orElse handleKillService

  def providingUser(user: User): Receive = {
    case RetrieveUser =>
      sender ! ProvideUser(user)
  }

  def waitingForToken: Receive = {
    case ReplyAccessToken(accessToken) =>
      context.become(withDefaultHandling(waitingForUser))
      retrieveUser(accessToken) pipeTo self
  }

  def waitingForUser: Receive = {
    case user: User => context.become(withDefaultHandling(providingUser(user)))
    case Status.Failure(_: UnableToAuthenticate.type) => // What to do when failure on getting User
      LogProvider.unsafeLogWithAction[F](
        UserInfo.Name,
        Debug,
        show"Failure on call to get User with error $UnableToAuthenticate"
      ) {
        fetchToken(expiredAccessToken = true)
      }
    case Status.Failure(error) => {
      // 1 - Retry
      LogProvider.unsafeLogWithAction[F](UserInfo.Name,
        Debug,
        show"Failure on call to get User with error $error"){
        fetchToken() // Failed to Get User to get access token and try again
      }
      // 2 - Close app (Scenario not necessary for now)
    }
  }

  override def receive: Receive = withDefaultHandling {
    case CommonMessages.ApplicationStarted =>
      fetchToken()
  }
}

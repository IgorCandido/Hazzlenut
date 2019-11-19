package hazzlenut.services.twitch.actor.helper

import akka.actor.{ActorContext, ActorRef}
import cats.Monad
import hazzlenut.HazzleNutZIO
import hazzlenut.handler.TwitchClientHandler
import hazzlenut.services.twitch.actor.UserInfo
import hazzlenut.services.twitch.actor.adapter.TwitchClient
import hazzlenut.util.{HttpClient, LogProvider}

object UserInfoInitializer {
  implicit val akkaUserInfoInitializer =
    new UserInfoInitializer[HazzleNutZIO] {
      override def initializeUserInfo(tokenHolder: ActorRef)(
        implicit context: ActorContext,
        twitchClientHandler: TwitchClientHandler[HazzleNutZIO],
        twitchClient: TwitchClient[HazzleNutZIO],
        httpClient: HttpClient[HazzleNutZIO],
        logProvider: LogProvider[HazzleNutZIO],
        monad: Monad[HazzleNutZIO]
      ): ActorRef = {
        context.actorOf(UserInfo.props[HazzleNutZIO](tokenHolder))
      }
    }
}

trait UserInfoInitializer[F[_]] {
  def initializeUserInfo(tokenHolder: ActorRef)(
    implicit context: ActorContext,
    twitchClientHandler: TwitchClientHandler[F],
    twitchClient: TwitchClient[F],
    httpClient: HttpClient[F],
    logProvider: LogProvider[F],
    monad: Monad[F]
  ): ActorRef
}

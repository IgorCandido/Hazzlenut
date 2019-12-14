package hazzlenut.services.twitch.actor.helper

import akka.actor.{ActorContext, ActorRef, ActorSystem, Props}
import cats.Monad
import hazzlenut.HazzleNutZIO
import hazzlenut.handler.TwitchClientHandler
import hazzlenut.services.twitch.actor.TokenGuardian.{Initializer, ServiceInitializer, ServiceType}
import hazzlenut.services.twitch.actor.{TokenGuardian, UserInfo}
import hazzlenut.services.twitch.actor.adapter.TwitchClient
import hazzlenut.util.{HttpClient, LogProvider}
import hazzlenut.util.ZIORuntime._

object UserInfoInitializer {
  implicit final case object akkaUserInfoInitializer extends UserInfoInitializer[HazzleNutZIO] {
      override def initializeUserInfo(
        propsF: Props => Props,
        tokenGuardian: ActorRef,
        tokenHolder: ActorRef
      )(implicit system: ActorSystem,
        twitchClientHandler: TwitchClientHandler[HazzleNutZIO],
        twitchClient: TwitchClient[HazzleNutZIO],
        httpClient: HttpClient[HazzleNutZIO],
        logProvider: LogProvider[HazzleNutZIO],
        monad: Monad[HazzleNutZIO]): ActorRef = {
        system.actorOf(
          UserInfo.props[HazzleNutZIO](tokenHolder)
        )
      }
    }

  def apply[F[_]](
    implicit userInfoInitializer: UserInfoInitializer[F]
  ): UserInfoInitializer[F] = userInfoInitializer

  def initializer[F[_]: TwitchClientHandler: TwitchClient: HttpClient: LogProvider: Monad: UserInfoInitializer](
    implicit system: ActorSystem,
  ): ServiceInitializer =
    ServiceInitializer(
      ServiceType.UserInfo,
      UserInfoInitializer[F].initializeUserInfo _
    )
}

trait UserInfoInitializer[F[_]] {
  def initializeUserInfo(propsF: Props => Props, tokenGuardian: ActorRef, tokenHolder: ActorRef)(
    implicit system: ActorSystem,
    twitchClientHandler: TwitchClientHandler[F],
    twitchClient: TwitchClient[F],
    httpClient: HttpClient[F],
    logProvider: LogProvider[F],
    monad: Monad[F]
  ): ActorRef
}

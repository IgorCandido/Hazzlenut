package hazzlenut.services.twitch

import akka.actor.{ActorContext, ActorRef}
import hazzlenut.errors.HazzlenutError
import hazzlenut.handler.TwitchClientHandler
import hazzlenut.util.HttpClient
import log.effect.LogWriter
import zio.ZIO

object UserInfoInitializer {
  implicit val akkaUserInfoInitializer =
    new UserInfoInitializer[ZIO[Any, HazzlenutError, ?]] {
      override def initializeUserInfo(tokenHolder: ActorRef)(
        implicit context: ActorContext,
        twitchClientHandler: TwitchClientHandler[ZIO[Any, HazzlenutError, ?]],
        twitchClient: TwitchClient[ZIO[Any, HazzlenutError, ?]],
        httpClient: HttpClient[ZIO[Any, HazzlenutError, ?]],
        loggerWriter: LogWriter[ZIO[Any, HazzlenutError, ?]]
      ): ActorRef = {
        context.actorOf(
          UserInfo.props[ZIO[Any, HazzlenutError, ?]](tokenHolder)
        )
      }
    }
}

trait UserInfoInitializer[F[_]] {
  def initializeUserInfo(tokenHolder: ActorRef)(
    implicit context: ActorContext,
    twitchClientHandler: TwitchClientHandler[F],
    twitchClient: TwitchClient[F],
    httpClient: HttpClient[F],
    logWriter: LogWriter[F]
  ): ActorRef
}

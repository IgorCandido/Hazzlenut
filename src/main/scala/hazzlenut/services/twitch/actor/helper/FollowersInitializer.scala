package hazzlenut.services.twitch.actor.helper

import akka.actor.{ActorRef, ActorSystem}
import cats.Monad
import hazzlenut.HazzleNutZIO
import hazzlenut.services.twitch.actor.{Followers, TokenGuardian}
import hazzlenut.services.twitch.actor.TokenGuardian.ServiceInitializer

import scala.concurrent.duration.FiniteDuration

trait FollowersInitializer[F[_]] {
  def initializeFollowers(
    pollingPeriod: FiniteDuration
  )(tokenGuardian: ActorRef, tokenHolder: ActorRef)(
    implicit system: ActorSystem,
    monad: Monad[F],
    executor: Executor[F]
  ): ActorRef
}

object FollowersInitializer {
  implicit object akkaFollowersInitializer
      extends FollowersInitializer[HazzleNutZIO] {
    override def initializeFollowers(pollingPeriod: FiniteDuration)(
      tokenGuardian: ActorRef,
      tokenHolder: ActorRef
    )(implicit system: ActorSystem,
      monad: Monad[HazzleNutZIO],
      executor: Executor[HazzleNutZIO]): ActorRef =
      system.actorOf(
        Followers.props[HazzleNutZIO](tokenGuardian, tokenHolder, pollingPeriod)
      )
  }

  def apply[F[_]](
    implicit followersInitializer: FollowersInitializer[F]
  ): FollowersInitializer[F] = followersInitializer

  def initializer[F[_]: FollowersInitializer](pollingPeriod: FiniteDuration)(implicit system: ActorSystem,
                                              monad: Monad[F],
                                              executor: Executor[F]) =
    ServiceInitializer(
      TokenGuardian.ServiceType.Followers,
      FollowersInitializer[F].initializeFollowers(pollingPeriod) _
    )
}

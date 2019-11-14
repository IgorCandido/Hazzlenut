package hazzlenut.handler

import akka.actor.ActorSystem
import akka.stream.Materializer
import hazzlenut.errors.HazzlenutError
import hazzlenut.services.twitch.{AccessToken, CommonReferences, TwitchClient}
import hazzlenut.services.twitch.model.{Follow, User}
import hazzlenut.util.HttpClient
import zio.ZIO
import zio.interop.catz._
import cats.implicits._

import scala.concurrent.Future

trait TwitchClientHandler[F[_]] {
  def retrieveUser(accessToken: AccessToken)(
    implicit twitchClient: TwitchClient[F],
    commonReferences: CommonReferences[F]
  ): Future[User]

  def retrieveFollowers(accessToken: AccessToken,
                        userId: String,
                        cursor: Option[String])(
    implicit twitchClient: TwitchClient[F],
    commonReferences: CommonReferences[F]
  ): Future[Seq[Follow]]
}

object TwitchClientHandler {
  object dsl {
    def retrieveUser[F[_]](accessToken: AccessToken)(
      implicit twitchClientHandler: TwitchClientHandler[F],
      twitchClient: TwitchClient[F],
      commonReferences: CommonReferences[F]
    ): Future[User] = twitchClientHandler.retrieveUser(accessToken)

    def retrieveFollowers[F[_]](accessToken: AccessToken,
                                userId: String,
                                cursor: Option[String])(
      implicit twitchClientHandler: TwitchClientHandler[F],
      twitchClient: TwitchClient[F],
      commonReferences: CommonReferences[F]
    ): Future[Seq[Follow]] =
      twitchClientHandler.retrieveFollowers(accessToken, userId, cursor)
  }

  implicit val twitchClientHandlerZIO =
    new TwitchClientHandler[ZIO[Any, HazzlenutError, ?]] {
      import hazzlenut.util.ZIORuntime._

      override def retrieveUser(accessToken: AccessToken)(
        implicit twitchClient: TwitchClient[ZIO[Any, HazzlenutError, ?]],
        commonReferences: CommonReferences[ZIO[Any, HazzlenutError, ?]]
      ): Future[User] = {
        val getUser = twitchClient.user(accessToken)

        runtime.unsafeRunToFuture(getUser)
      }

      override def retrieveFollowers(accessToken: AccessToken,
                                     userId: String,
                                     cursor: Option[String])(
        implicit twitchClient: TwitchClient[ZIO[Any, HazzlenutError, ?]],
        commonReferences: CommonReferences[ZIO[Any, HazzlenutError, ?]]
      ): Future[Seq[Follow]] = {
        val getFollower = twitchClient.followers(accessToken, userId, cursor)

        runtime.unsafeRunToFuture(getFollower)
      }
    }
}

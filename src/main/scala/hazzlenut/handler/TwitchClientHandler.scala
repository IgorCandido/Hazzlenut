package hazzlenut.handler

import akka.actor.ActorSystem
import akka.stream.Materializer
import hazzlenut.errors.HazzlenutError
import hazzlenut.services.twitch.{AccessToken, TwitchClient}
import hazzlenut.services.twitch.model.User
import scalaz.zio.ZIO
import scalaz.zio.interop.catz._

import scala.concurrent.Future

trait TwitchClientHandler {
  def retrieveUser(accessToken: AccessToken)(
    implicit twitchClient: TwitchClient[ZIO[Any, HazzlenutError, ?]],
    actorSystem: ActorSystem,
    materializer: Materializer
  ): Future[User]
}

object TwitchClientHandler {
  object dsl {
    def retrieveUser(accessToken: AccessToken)(
      implicit twitchClientHandler: TwitchClientHandler,
      twitchClient: TwitchClient[ZIO[Any, HazzlenutError, ?]],
      actorSystem: ActorSystem,
      materializer: Materializer
    ): Future[User] = twitchClientHandler.retrieveUser(accessToken)
  }

  implicit val twitchClientHandlerZIO = new TwitchClientHandler {
    import hazzlenut.util.ZIORuntime._

    override def retrieveUser(accessToken: AccessToken)(
      implicit twitchClient: TwitchClient[ZIO[Any, HazzlenutError, ?]],
      actorSystem: ActorSystem,
      materializer: Materializer
    ): Future[User] = {

      val getUser = twitchClient.user(accessToken)

      runtime.unsafeRunToFuture(getUser)
    }

  }
}

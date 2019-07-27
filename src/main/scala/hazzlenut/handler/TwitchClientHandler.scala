package hazzlenut.handler

import akka.actor.ActorSystem
import akka.stream.Materializer
import hazzlenut.errors.HazzlenutError
import hazzlenut.services.twitch.{AccessToken, TwitchClient}
import hazzlenut.services.twitch.model.User
import hazzlenut.util.HttpClient
import zio.ZIO
import zio.interop.catz._
import cats.implicits._

import scala.concurrent.Future

trait TwitchClientHandler[F[_]] {
  def retrieveUser(accessToken: AccessToken)(
    implicit twitchClient: TwitchClient[F],
    httpClient: HttpClient[F],
    actorSystem: ActorSystem,
    materializer: Materializer
  ): Future[User]
}

object TwitchClientHandler {
  object dsl {
    def retrieveUser[F[_]](accessToken: AccessToken)(
      implicit twitchClientHandler: TwitchClientHandler[F],
      twitchClient: TwitchClient[F],
      httpClient: HttpClient[F],
      actorSystem: ActorSystem,
      materializer: Materializer
    ): Future[User] = twitchClientHandler.retrieveUser(accessToken)
  }

  implicit val twitchClientHandlerZIO = new TwitchClientHandler[ZIO[Any, HazzlenutError, ?]] {
    import hazzlenut.util.ZIORuntime._

    override def retrieveUser(accessToken: AccessToken)(
      implicit twitchClient: TwitchClient[ZIO[Any, HazzlenutError, ?]],
      httpClient: HttpClient[ZIO[Any, HazzlenutError, ?]],
      actorSystem: ActorSystem,
      materializer: Materializer
    ): Future[User] = {

      val getUser = twitchClient.user(accessToken)

      runtime.unsafeRunToFuture(getUser)
    }

  }
}

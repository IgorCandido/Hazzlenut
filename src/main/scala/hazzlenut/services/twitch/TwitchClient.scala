package hazzlenut.services.twitch

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{ActorMaterializer, Materializer}
import hazzlenut.errors.HazzlenutError
import hazzlenut.errors.HazzlenutError.{ThrowableError, UnableToFetchUserInformation}
import hazzlenut.services.twitch.model.{TwitchReply, User}
import scalaz.zio.ZIO

trait TwitchClient[F[_]] {
  def user(accessToken: AccessToken)(implicit actorSystem: ActorSystem,
                                     materializer: Materializer): F[User]
}

object TwitchClient {

  object dsl {
    def retrieveUser[F[_]](accessToken: AccessToken)(
      implicit twitchClient: TwitchClient[F],
      actorSystem: ActorSystem,
      materializer: Materializer
    ) =
      twitchClient.user(accessToken)
  }

  implicit val twitchClientZIO = new TwitchClient[ZIO[Any, HazzlenutError, ?]] {
    override def user(accessToken: AccessToken)(
      implicit actorSystem: ActorSystem,
      materializer: Materializer
    ): ZIO[Any, HazzlenutError, User] = {
      ZIO
        .fromFuture { implicit ec =>
          Http()
            .singleRequest(
              HttpRequest(uri = "https://api.twitch.tv/helix/users")
                .addCredentials(OAuth2BearerToken(accessToken.accessToken))
            )
            .flatMap { user =>
              Unmarshal(user.entity)
                .to[TwitchReply[User]]
                .map(reply => reply.data.headOption)
            }
        }
        .flatMap { user =>
          user match {
            case None => ZIO.fail(UnableToFetchUserInformation)
            case Some(u) => ZIO.succeed(u)
          } // Would prefer to use fold of option
            // but .fold(=> B)(A => B): B enforces the generics on B to match regardless of Contravariance
        }
        .mapError(throwable => ThrowableError(throwable))
    }
  }

}

package hazzlenut.services.twitch

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import hazzlenut.errors.HazzlenutError
import hazzlenut.errors.HazzlenutError.ThrowableError
import hazzlenut.services.twitch.model.{TwitchReply, User}
import scalaz.zio.ZIO

trait TwitchClient[F[_]] {
  def user(accessToken: AccessToken)(implicit actorSystem: ActorSystem,
                                     materializer: ActorMaterializer): F[User]
}

object TwitchClient {

  implicit val twitchClientZIO = new TwitchClient[ZIO[Any, HazzlenutError, ?]] {
    override def user(accessToken: AccessToken)(
      implicit actorSystem: ActorSystem,
      materializer: ActorMaterializer
    ): ZIO[Any, HazzlenutError, User] = {
      ZIO
        .fromFuture { implicit ec =>
          Http()
            .singleRequest(
              HttpRequest(uri = "https://api.twitch.tv/helix/users")
                .addCredentials(OAuth2BearerToken(accessToken.accessToken))
            )
            .flatMap { user =>
              Unmarshal(user.entity).to[TwitchReply[User]].map(reply => reply.data.headOption)
            }
        }.flatMap{
          _.fold(ZIO.fail())
        }
        .mapError(throwable => ThrowableError(throwable))
    }
  }

}

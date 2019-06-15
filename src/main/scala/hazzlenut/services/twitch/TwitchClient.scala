package hazzlenut.services.twitch

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.model.{HttpRequest, ResponseEntity}
import akka.stream.Materializer
import hazzlenut.errors.HazzlenutError
import hazzlenut.errors.HazzlenutError.{ThrowableError, UnableToFetchUserInformation}
import hazzlenut.services.twitch.model.{TwitchReply, User}
import hazzlenut.util.{HttpClient, UnmarshallerEntiy}
import scalaz.zio.ZIO

// TODO Move the logic into the type class (polymorphic)

trait TwitchClient[F[_]] {
  def user(accessToken: AccessToken)(
    implicit actorSystem: ActorSystem,
    materializer: Materializer,
    httpClient: HttpClient[F],
    unmarshallerEntiy: UnmarshallerEntiy[F]
  ): F[User]
}

object TwitchClient {

  object dsl {
    def retrieveUser[F[_]](accessToken: AccessToken)(
      implicit twitchClient: TwitchClient[F],
      actorSystem: ActorSystem,
      materializer: Materializer,
      httpClient: HttpClient[F],
      unmarshallerEntiy: UnmarshallerEntiy[F]
    ) =
      twitchClient.user(accessToken)
  }

  implicit val twitchClientZIO =
    new TwitchClient[ZIO[Any, HazzlenutError, ?]] {
      override def user(accessToken: AccessToken)(
        implicit actorSystem: ActorSystem,
        materializer: Materializer,
        httpClient: HttpClient[ZIO[Any, HazzlenutError, ?]],
        unmarshallerEntiy: UnmarshallerEntiy[ZIO[Any, HazzlenutError, ?]]
      ): ZIO[Any, HazzlenutError, User] = {
        for {
          httpResult <- httpClient.request(
            HttpRequest(uri = "https://api.twitch.tv/helix/users")
              .addCredentials(OAuth2BearerToken(accessToken.accessToken))
          )
          userMaybe <- unmarshallerEntiy
            .unmarshal[ResponseEntity, TwitchReply[User]](httpResult.entity)
            .map { reply =>
              reply.data.headOption
            } // TODO Convert the layer into type class method that aggregates both
          user <- ZIO
            .fromOption(userMaybe)
            .mapError(_ => UnableToFetchUserInformation)
        } yield user
      }
    }

}

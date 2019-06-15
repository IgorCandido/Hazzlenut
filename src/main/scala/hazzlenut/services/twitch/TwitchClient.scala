package hazzlenut.services.twitch

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.model.{HttpRequest, ResponseEntity}
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream.Materializer
import cats.implicits._
import cats.{Monad, MonadError}
import hazzlenut.errors.HazzlenutError
import hazzlenut.errors.HazzlenutError.UnableToFetchUserInformation
import hazzlenut.services.twitch.model.{TwitchReply, User}
import hazzlenut.util.{HttpClient, UnmarshallerEntiy}
import scalaz.zio.ZIO

// TODO Move the logic into the type class (polymorphic)

trait TwitchClient[F[_]] {

  def fromOption[Out](optionOf: Option[Out], hazzlenutError: HazzlenutError)(
    implicit monadError: MonadError[F, HazzlenutError]
  ): F[Out]

  protected def doRequest[Out](
    url: String,
    accessToken: AccessToken,
    hazzlenutError: HazzlenutError
  )(implicit actorSystem: ActorSystem,
    materializer: Materializer,
    httpClient: HttpClient[F],
    unmarshallerEntiy: UnmarshallerEntiy[F],
    unmarshaller: Unmarshaller[ResponseEntity, TwitchReply[Out]],
    monadF: Monad[F],
    monadError: MonadError[F, HazzlenutError]): F[Out] = {
    for {
      httpResult <- httpClient.request(
        HttpRequest(uri = "https://api.twitch.tv/helix/users")
          .addCredentials(OAuth2BearerToken(accessToken.accessToken))
      )
      outMaybe <- unmarshallerEntiy
        .unmarshal[ResponseEntity, TwitchReply[Out]](httpResult.entity)
        .map { reply =>
          reply.data.headOption
        }
      out <- fromOption(outMaybe, hazzlenutError)
    } yield out
  }

  def user(accessToken: AccessToken)(
    implicit actorSystem: ActorSystem,
    materializer: Materializer,
    httpClient: HttpClient[F],
    unmarshallerEntiy: UnmarshallerEntiy[F],
    monadF: Monad[F],
    monadError: MonadError[F, HazzlenutError]
  ): F[User]
}

object TwitchClient {

  object dsl {
    def retrieveUser[F[_]](accessToken: AccessToken)(
      implicit twitchClient: TwitchClient[F],
      actorSystem: ActorSystem,
      materializer: Materializer,
      httpClient: HttpClient[F],
      unmarshallerEntiy: UnmarshallerEntiy[F],
      monadF: Monad[F],
      monadError: MonadError[F, HazzlenutError]
    ) =
      twitchClient.user(accessToken)
  }

  implicit val twitchClientZIO =
    new TwitchClient[ZIO[Any, HazzlenutError, ?]] {
      override def user(accessToken: AccessToken)(
        implicit actorSystem: ActorSystem,
        materializer: Materializer,
        httpClient: HttpClient[ZIO[Any, HazzlenutError, ?]],
        unmarshallerEntiy: UnmarshallerEntiy[ZIO[Any, HazzlenutError, ?]],
        monadF: Monad[ZIO[Any, HazzlenutError, ?]],
        monadError: MonadError[ZIO[Any, HazzlenutError, ?], HazzlenutError]
      ): ZIO[Any, HazzlenutError, User] = {
        for {
          user <- doRequest[User](
            "https://api.twitch.tv/helix/users",
            accessToken,
            UnableToFetchUserInformation
          )
        } yield user
      }

      override def fromOption[Out](optionOf: Option[Out],
                                   hazzlenutError: HazzlenutError)(
        implicit monadError: MonadError[ZIO[Any, HazzlenutError, ?],
                                        HazzlenutError]
      ): ZIO[Any, HazzlenutError, Out] = {
        ZIO
          .fromOption(optionOf)
          .mapError(_ => hazzlenutError)
      }
    }

}

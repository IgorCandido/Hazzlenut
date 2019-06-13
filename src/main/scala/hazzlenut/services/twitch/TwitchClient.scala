package hazzlenut.services.twitch

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import cats.Monad
import cats.implicits._
import com.sun.net.httpserver.Authenticator.Success
import hazzlenut.errors.HazzlenutError
import hazzlenut.errors.HazzlenutError.{ThrowableError, UnableToFetchUserInformation}
import hazzlenut.services.twitch.model.{TwitchReply, User}
import hazzlenut.util.HttpClient
import scalaz.zio.ZIO

import scala.concurrent.ExecutionContext
import scala.util.Success

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

  implicit def twitchClientZIO[F[_]: Monad](
    implicit httpClient: HttpClient[F]
  ) =
    new TwitchClient[ZIO[Any, HazzlenutError, ?]] {
      override def user(accessToken: AccessToken)(
        implicit actorSystem: ActorSystem,
        materializer: Materializer,
        executionContext: ExecutionContext
      ): ZIO[Any, HazzlenutError, User] = {
        ZIO
          .effect {
            httpClient
              .request(
                HttpRequest(uri = "https://api.twitch.tv/helix/users")
                  .addCredentials(OAuth2BearerToken(accessToken.accessToken))
              ).flatMap { user =>
                Unmarshal(user.entity)
                  .to[TwitchReply[User]]
                  .map(reply => reply.data.headOption)
              }
          }
          .flatMap { user =>
            user match {
              case None    => ZIO.fail(UnableToFetchUserInformation)
              case Some(u) => ZIO.succeed(u)
            } // Would prefer to use fold of option
          // but .fold(=> B)(A => B): B enforces the generics on B to match regardless of Contravariance
          }
          .mapError(throwable => ThrowableError(throwable))
      }
    }

}

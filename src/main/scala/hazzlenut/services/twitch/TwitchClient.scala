package hazzlenut.services.twitch

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.model.{
  HttpRequest,
  HttpResponse,
  ResponseEntity,
  StatusCodes
}
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream.Materializer
import cats.implicits._
import cats.{Monad, MonadError}
import hazzlenut.errors.HazzlenutError
import hazzlenut.errors.HazzlenutError.{
  HttpError,
  UnableToAuthenticate,
  UnableToFetchFollowers,
  UnableToFetchUserInformation
}
import hazzlenut.services.twitch.model.{TwitchError, TwitchReply, User}
import hazzlenut.util.{HttpClient, UnmarshallerEntiy}
import scalaz.zio.ZIO

trait TwitchClient[F[_]] {

  def extractErrorfromTwitchError(
    implicit unmarshallerEntiy: UnmarshallerEntiy[F],
    unmarshaller: Unmarshaller[ResponseEntity, TwitchError],
    monadErrorHazzlenut: MonadError[F, HazzlenutError],
    materializer: Materializer
  ): (HttpResponse) => F[String] = { response =>
    unmarshallerEntiy.unmarshal[ResponseEntity, TwitchError](response.entity).map(_.message)
  }

  def handleUnAuthorized[Out](
    implicit monadError: MonadError[F, HazzlenutError]
  ): PartialFunction[HazzlenutError, F[Seq[Out]]] = {
    case HttpError(StatusCodes.Unauthorized.intValue, _) =>
      monadError.raiseError(UnableToAuthenticate)
  }

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
    monadErrorThrowable: MonadError[F, HazzlenutError]): F[Out] = {
    for {
      httpResult <- doRequestSeq[Out](url, accessToken, hazzlenutError)
      outMaybe = httpResult.headOption
      out <- fromOption(outMaybe, hazzlenutError)
    } yield out
  }

  protected def doRequestSeq[Out](
    url: String,
    accessToken: AccessToken,
    hazzlenutError: HazzlenutError
  )(implicit actorSystem: ActorSystem,
    materializer: Materializer,
    httpClient: HttpClient[F],
    unmarshallerEntiy: UnmarshallerEntiy[F],
    unmarshaller: Unmarshaller[ResponseEntity, TwitchReply[Out]],
    monadF: Monad[F],
    monadErrorThrowable: MonadError[F, HazzlenutError]): F[Seq[Out]] = {
    (for {
      httpResult <- httpClient.request(
        HttpRequest(uri = url)
          .addCredentials(OAuth2BearerToken(accessToken.accessToken)),
        extractErrorfromTwitchError
      )
      outMaybe <- unmarshallerEntiy
        .unmarshal[ResponseEntity, TwitchReply[Out]](httpResult.entity)
      out <- fromOption[Seq[Out]](
        Option(outMaybe.data.toSeq).filter(_.nonEmpty),
        hazzlenutError
      )
    } yield out).recoverWith {handleUnAuthorized}
  }

  def user(accessToken: AccessToken)(
    implicit actorSystem: ActorSystem,
    materializer: Materializer,
    httpClient: HttpClient[F],
    unmarshallerEntiy: UnmarshallerEntiy[F],
    monadF: Monad[F],
    monadErrorThrowable: MonadError[F, HazzlenutError]
  ): F[User]

  def followers(accessToken: AccessToken, userId: String)(
    implicit actorSystem: ActorSystem,
    materializer: Materializer,
    httpClient: HttpClient[F],
    unmarshallerEntiy: UnmarshallerEntiy[F],
    monadF: Monad[F],
    monadErrorThrowable: MonadError[F, HazzlenutError]
  ): F[Seq[User]]
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
        monadErrorThrowable: MonadError[ZIO[Any, HazzlenutError, ?],
                                        HazzlenutError]
      ): ZIO[Any, HazzlenutError, User] =
        for {
          user <- doRequest[User](
            "https://api.twitch.tv/helix/users",
            accessToken,
            UnableToFetchUserInformation
          )
        } yield user

      override def followers(accessToken: AccessToken, userId: String)(
        implicit actorSystem: ActorSystem,
        materializer: Materializer,
        httpClient: HttpClient[ZIO[Any, HazzlenutError, ?]],
        unmarshallerEntiy: UnmarshallerEntiy[ZIO[Any, HazzlenutError, ?]],
        monadF: Monad[ZIO[Any, HazzlenutError, ?]],
        monadErrorThrowable: MonadError[ZIO[Any, HazzlenutError, ?],
                                        HazzlenutError]
      ): ZIO[Any, HazzlenutError, Seq[User]] =
        for {
          users <- doRequestSeq[User](
            s"https://api.twitch.tv/helix/users/follows?to_id=$userId",
            accessToken,
            UnableToFetchFollowers
          )
        } yield users

      override def fromOption[Out](optionOf: Option[Out],
                                   hazzlenutError: HazzlenutError)(
        implicit monadError: MonadError[ZIO[Any, HazzlenutError, ?],
                                        HazzlenutError]
      ): ZIO[Any, HazzlenutError, Out] =
        ZIO
          .fromOption(optionOf)
          .mapError(_ => hazzlenutError)
    }
}

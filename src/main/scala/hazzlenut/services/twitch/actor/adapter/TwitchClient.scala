package hazzlenut.services.twitch.actor.adapter

import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.model.{
  HttpRequest,
  HttpResponse,
  ResponseEntity,
  StatusCodes
}
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream.Materializer
import cats.MonadError
import cats.implicits._
import hazzlenut.errors.HazzlenutError
import hazzlenut.errors.HazzlenutError.{
  HttpError,
  UnableToAuthenticate,
  UnableToFetchFollowers,
  UnableToFetchUserInformation
}
import hazzlenut.services.twitch.adapters.AccessToken
import hazzlenut.services.twitch.helper.CommonReferences
import hazzlenut.services.twitch.helper.CommonReferences.extractors._
import hazzlenut.services.twitch.model._
import hazzlenut.util.{HttpClient, UnmarshallerEntiy}
import zio.ZIO

trait TwitchClient[F[_]] {
  def extractErrorfromTwitchError(
    implicit unmarshallerEntiy: UnmarshallerEntiy[F],
    unmarshaller: Unmarshaller[ResponseEntity, TwitchError],
    monadErrorHazzlenut: MonadError[F, HazzlenutError],
    materializer: Materializer
  ): (HttpResponse) => F[String] = { response =>
    unmarshallerEntiy
      .unmarshal[ResponseEntity, TwitchError](response.entity)
      .map(_.message)
  }

  def handleUnAuthorizedNonSeq[Out](
    implicit monadError: MonadError[F, HazzlenutError]
  ): PartialFunction[HazzlenutError, F[Out]] = {
    case HttpError(StatusCodes.Unauthorized.intValue, _) =>
      monadError.raiseError(UnableToAuthenticate)
  }

  def handleUnAuthorized[Out](
    implicit monadError: MonadError[F, HazzlenutError]
  ): PartialFunction[HazzlenutError, F[InternalRepresentationTwitchReply[Out]]] = {
    case HttpError(StatusCodes.Unauthorized.intValue, _) =>
      monadError.raiseError(UnableToAuthenticate)
  }

  def fromOption[Out](optionOf: Option[Out], hazzlenutError: HazzlenutError)(
    implicit monadError: MonadError[F, HazzlenutError]
  ): F[Out]

  protected def doRequestBase[Out](url: String,
                                   accessToken: AccessToken,
                                   hazzlenutError: HazzlenutError)(
                                    implicit commonReferences: CommonReferences[F],
                                    unmarshaller: Unmarshaller[ResponseEntity, TwitchReply[Out]],
                                    monadErrorThrowable: MonadError[F, HazzlenutError]
                                  ): F[InternalRepresentationTwitchReply[Out]] =
    (for {
      httpResult <- HttpClient[F].request(
        HttpRequest(uri = url)
          .addCredentials(OAuth2BearerToken(accessToken.accessToken)),
        extractErrorfromTwitchError
      )
      outMaybe <- UnmarshallerEntiy[F]
        .unmarshal[ResponseEntity, TwitchReply[Out]](httpResult.entity)
      out <- fromOption[Seq[Out]](
        outMaybe.data.map(_.toSeq).filter(_.nonEmpty),
        hazzlenutError
      )
    } yield
      InternalRepresentationTwitchReply(out, outMaybe.pagination, outMaybe.total)).recoverWith {
      handleUnAuthorized
    }

  protected def doRequest[Out](
    url: String,
    accessToken: AccessToken,
    hazzlenutError: HazzlenutError
  )(implicit commonReferences: CommonReferences[F],
    unmarshaller: Unmarshaller[ResponseEntity, TwitchReply[Out]],
    monadErrorThrowable: MonadError[F, HazzlenutError]): F[Out] =
    for {
      httpResult <- doRequestBase[Out](url, accessToken, hazzlenutError)
      outMaybe = httpResult.seq.headOption
      out <- fromOption(outMaybe, hazzlenutError)
    } yield out

  protected def doRequestSeq[Out](url: String,
                                  accessToken: AccessToken,
                                  hazzlenutError: HazzlenutError)(
    implicit commonReferences: CommonReferences[F],
    unmarshaller: Unmarshaller[ResponseEntity, TwitchReply[Out]],
    monadErrorThrowable: MonadError[F, HazzlenutError]
  ): F[TwitchSeqWithMeta[Out]] =
    for {
      httpResult <- doRequestBase[Out](url, accessToken, hazzlenutError)
      pagination <- fromOption(httpResult.pagination, hazzlenutError)
      total <- fromOption(httpResult.total, hazzlenutError)
    } yield TwitchSeqWithMeta(httpResult.seq, pagination, total)

  def user(accessToken: AccessToken)(
    implicit commonReferences: CommonReferences[F],
    monadErrorThrowable: MonadError[F, HazzlenutError]
  ): F[User]

  def followers(accessToken: AccessToken,
                userId: String,
                cursor: Option[String])(
    implicit commonReferences: CommonReferences[F],
    monadErrorThrowable: MonadError[F, HazzlenutError]
  ): F[TwitchSeqWithMeta[Follow]]
}

object TwitchClient {
  val USERS_URL = "https://api.twitch.tv/helix/users"
  val FOLLOWERS_URL =
    "https://api.twitch.tv/helix/users/follows?to_id=%s&direction=asc"

  object dsl {
    def retrieveUser[F[_]](accessToken: AccessToken)(
      implicit twitchClient: TwitchClient[F],
      commonReferences: CommonReferences[F],
      monadError: MonadError[F, HazzlenutError]
    ) =
      twitchClient.user(accessToken)
  }

  implicit val twitchClientZIO =
    new TwitchClient[ZIO[Any, HazzlenutError, ?]] {
      override def user(accessToken: AccessToken)(
        implicit commonReferences: CommonReferences[
          ZIO[Any, HazzlenutError, ?]
        ],
        monadErrorThrowable: MonadError[ZIO[Any, HazzlenutError, ?],
                                        HazzlenutError]
      ): ZIO[Any, HazzlenutError, User] = {
        import TwitchReply._

        for {
          user <- doRequest[User](
            USERS_URL,
            accessToken,
            UnableToFetchUserInformation
          )
        } yield user
      }

      private def addQueryStringParameter(
        url: String
      )(name: String, value: Option[String]): String =
        (if (url.exists(_ == '?')) '&' else '?', value) match {
          case (separator, Some(v)) => s"$url$separator$name=$v"
          case (_, None)            => url
        }

      override def followers(accessToken: AccessToken,
                             userId: String,
                             cursor: Option[String])(
        implicit commonReferences: CommonReferences[
          ZIO[Any, HazzlenutError, ?]
        ],
        monadErrorThrowable: MonadError[ZIO[Any, HazzlenutError, ?],
                                        HazzlenutError]
      ): ZIO[Any, HazzlenutError, TwitchSeqWithMeta[Follow]] = {
        import TwitchReply._
        for {
          followers <- doRequestSeq[Follow](
            addQueryStringParameter(FOLLOWERS_URL.format(userId))(
              "cursor",
              cursor
            ),
            accessToken,
            UnableToFetchFollowers
          )
        } yield followers
      }

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

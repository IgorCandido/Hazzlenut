package services.twitch

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.stream.{ActorMaterializer, Materializer}
import cats.implicits._
import hazzlenut.errors.HazzlenutError
import hazzlenut.errors.HazzlenutError._
import hazzlenut.services.twitch.AccessToken
import hazzlenut.services.twitch.model.{TwitchReply, User}
import hazzlenut.util.{HttpClient, UnmarshallerEntiy}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import utils.TestIO

import scala.concurrent.ExecutionContext

class TwitchClientSpec extends WordSpec with Matchers with BeforeAndAfterAll {

  implicit val system: ActorSystem = ActorSystem()
  implicit val ec: ExecutionContext = system.dispatcher
  implicit val mat: Materializer = ActorMaterializer()

  val accessToken =
    AccessToken("dummyAccessToken", "bearer", 2000, "v3m3mn983nvu3nuv3".some)
  val defaultReply =
    "{\"data\":[{\"id\":\"55407179\",\"login\":\"igrcndd\",\"display_name\":\"igrcndd\",\"type\":\"\",\"broadcaster_type\":\"affiliate\",\"description\":\"I am a stream hobbyist. Trying to put enough time to put something that is worth watching with decent quality. I really enjoy interacting with viewers.Twitter: @igrcndd\",\"profile_image_url\":\"https://static-cdn.jtvnw.net/jtv_user_pictures/42c29bc3-8267-490c-84c8-5d695b16a62f-profile_image-300x300.png\",\"offline_image_url\":\"https://static-cdn.jtvnw.net/jtv_user_pictures/d71aedc3628eae34-channel_offline_image-1920x1080.jpeg\",\"view_count\":6420}]}"

  "TwitchClient" should {
    "get user when access token provided" in {
      implicit val httpClient = TestIO.httpClientSucess(defaultReply)

      val client = TestIO.twitchClient
      val reply = client.user(accessToken)
      val userOrError = reply.result

      userOrError.fold(
        error => fail("failed to retrieve user", error),
        user => {
          user should have(
            'login ("igrcndd"),
            'type (""),
            'id ("55407179"),
            'broadcaster_type ("affiliate"),
            'email (None),
            'display_name ("igrcndd"),
            'description (
              "I am a stream hobbyist. Trying to put enough time to put something that is worth watching with decent quality. I really enjoy interacting with viewers.Twitter: @igrcndd"
            ),
            'view_count (6420)
          )
        }
      )
    }

    "get user with error on http connection" in {
      val throwable = new Exception("There an error")

      implicit val httpClient = TestIO.httpClient(
        TestIO(
          Either.left[HazzlenutError, HttpResponse](ThrowableError(throwable))
        )
      )

      val client = TestIO.twitchClient
      val reply = client.user(accessToken)
      val userOrError = reply.result

      userOrError.fold(
        error =>
          error match {
            case ConnectionError(t) => t should ===(throwable)
            case _                  => fail("Unknown error")
        },
        _ => fail("Should have failed")
      )
    }
    "get user with unAuthorized" in {

      implicit val httpClient = TestIO.httpClientWithCustomStatusCode(
        "{\"message\":\"Invalid Token\",\"status\":401,\"error\":\"Unauthorized\"}",
        StatusCodes.Unauthorized
      )

      val client = TestIO.twitchClient
      val reply = client.user(accessToken)
      val userOrError = reply.result

      userOrError.fold(
        error =>
          error match {
            case UnableToAuthenticate => succeed
            case _                    => fail("Unknown error")
        },
        _ => fail("Should have failed")
      )
    }

    "get user with BadRequest" in {

      val `400Reply` =
        "{\n\"error\": \"Bad Request\",\n\"status\": 400,\n\"message\": \"Must provide either from_id or to_id\"\n}"

      implicit val httpClient = TestIO.httpClientWithCustomStatusCode(
        `400Reply`,
        StatusCodes.BadRequest
      )

      val client = TestIO.twitchClient
      val reply = client.user(accessToken)
      val userOrError = reply.result

      userOrError.fold(
        error =>
          error match {
            case HttpError(_, message) =>
              message should ===("Must provide either from_id or to_id")
            case _ => fail("Unknown error")
        },
        _ => fail("Should have failed")
      )
    }

    "get user with error on unmarshelling" in {
      val throwable = new Exception("Couldn't parse")

      implicit val httpClient = TestIO.httpClientSucess(defaultReply)

      implicit val unmarshaller = TestIO.unmarshallerEntiy(
        TestIO(
          Either
            .left[HazzlenutError, TwitchReply[User]](ThrowableError(throwable))
        )
      )

      val client = TestIO.twitchClient
      val reply = client.user(accessToken)
      val userOrError = reply.result

      userOrError.fold(
        error =>
          error match {
            case UnmarshallError(t) => t should ===(throwable)
            case _                  => fail("Unknown error")
        },
        _ => fail("Should have failed")
      )
    }

    "get user returning no users" in {
      implicit val httpClient: HttpClient[TestIO] =
        TestIO.httpClientSucess(defaultReply)

      implicit val unmarshaller: UnmarshallerEntiy[TestIO] =
        TestIO.unmarshallerEntiy(
          TestIO(
            Either.right[HazzlenutError, TwitchReply[User]](
              TwitchReply(total= None, data = Option(Array.empty[User]), pagination = None)
            )
          )
        )

      val client = TestIO.twitchClient
      val reply = client.user(accessToken)
      val userOrError = reply.result

      userOrError.fold(
        error =>
          error match {
            case UnableToFetchUserInformation => succeed
            case _                            => fail("Unknown error")
        },
        _ => fail("Should have failed")
      )
    }
  }

}

package services.twitch

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.{ActorMaterializer, Materializer}
import cats.MonadError
import cats.implicits._
import hazzlenut.errors.HazzlenutError
import hazzlenut.services.twitch.{AccessToken, TwitchClient}
import hazzlenut.util.HttpClient
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import utils.TestIO

import scala.concurrent.ExecutionContext

class TwitchClientSpec
    extends WordSpec
    with Matchers
    with BeforeAndAfterAll {

  implicit val system: ActorSystem = ActorSystem()
  implicit val ec: ExecutionContext = system.dispatcher
  implicit val mat: Materializer = ActorMaterializer()

  val accessToken = AccessToken("dummyAccessToken", "bearer", 2000, "v3m3mn983nvu3nuv3".some)
  val defaultReply = "{\"data\":[{\"id\":\"55407179\",\"login\":\"igrcndd\",\"display_name\":\"igrcndd\",\"type\":\"\",\"broadcaster_type\":\"affiliate\",\"description\":\"I am a stream hobbyist. Trying to put enough time to put something that is worth watching with decent quality. I really enjoy interacting with viewers.Twitter: @igrcndd\",\"profile_image_url\":\"https://static-cdn.jtvnw.net/jtv_user_pictures/42c29bc3-8267-490c-84c8-5d695b16a62f-profile_image-300x300.png\",\"offline_image_url\":\"https://static-cdn.jtvnw.net/jtv_user_pictures/d71aedc3628eae34-channel_offline_image-1920x1080.jpeg\",\"view_count\":6420}]}"

  "TwitchClient" should {
    "get user when access token provided" in {
      implicit val httpClient = new HttpClient[TestIO] {
        override def request(httpRequest: HttpRequest): TestIO[HttpResponse] =
          implicitly[MonadError[TestIO, HazzlenutError]].pure(HttpResponse(entity = defaultReply))
      }

      val client = TwitchClient.twitchClientZIO

      val reply = client.user(accessToken)
    }
  }

}

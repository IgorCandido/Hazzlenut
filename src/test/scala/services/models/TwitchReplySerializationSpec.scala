package services.models

import akka.actor.ActorSystem
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{ActorMaterializer, Materializer}
import cats.implicits._
import hazzlenut.services.twitch.model.{Follow, Pagination, TwitchReply, User}
import org.scalatest.{Matchers, WordSpec}
import utils.{FollowersReplyGen, UserGen}

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration._

class TwitchReplySerializationSpec extends WordSpec with Matchers {
  "TwitchReply serialization" should {
    "Properly serialize a twitchReply User" in {
      val twitchReply = TwitchReply[User](
        total = None,
        data = List(UserGen.getSample()).some,
        pagination = None
      )
      val serialized =
        TwitchReply.twitchReplyFormat[User].write(twitchReply).prettyPrint
      serialized.length should be > (0)
      serialized should include("broadcaster_type")
      serialized should include("description")
      serialized should include("display_name")
      serialized should include("email")
      serialized should include("id")
      serialized should include("login")
      serialized should include("offline_image_url")
      serialized should include("profile_image_url")
      serialized should include("type")
      serialized should include("view_count")
    }

    "Properly serialize a twitchReply Follow" in {
      val twitchReply = TwitchReply[Follow](
        total = 2L.some,
        data = FollowersReplyGen.getFollowersSeqSample().some,
        pagination = Pagination(cursor = "1234").some
      )
      val serialized =
        TwitchReply.twitchReplyFormat[Follow].write(twitchReply).prettyPrint
      serialized.length should be > (0)
      serialized should include("from_id")
      serialized should include("from_name")
      serialized should include("to_id")
      serialized should include("to_name")
      serialized should include("followed_at")
      serialized should include("total")
      serialized should include("cursor")
    }
  }

  "Twitch deserialization" should {
    val defaultUserReply =
      """{
        |"data":
        | [
        |   {
        |    "id":"55407179",
        |    "login":"igrcndd",
        |    "display_name":"igrcndd",
        |    "type":"",
        |    "broadcaster_type":"affiliate",
        |    "description":"I am a stream hobbyist. Trying to put enough time to put something that is worth watching with decent quality. I really enjoy interacting with viewers.Twitter: @igrcndd",
        |    "profile_image_url":"https://static-cdn.jtvnw.net/jtv_user_pictures/42c29bc3-8267-490c-84c8-5d695b16a62f-profile_image-300x300.png",
        |    "offline_image_url":"https://static-cdn.jtvnw.net/jtv_user_pictures/d71aedc3628eae34-channel_offline_image-1920x1080.jpeg",
        |    "view_count":6420
        |    }
        |  ]
        |}""".stripMargin

    val defaultFollowersReply =
      """{
        |   "total": 12345,
        |   "data":
        |   [
        |      {
        |         "from_id": "171003792",
        |         "from_name": "IIIsutha067III",
        |         "to_id": "23161357",
        |         "to_name": "LIRIK",
        |         "followed_at": "2017-08-22T22:55:24Z"
        |      },
        |      {
        |         "from_id": "113627897",
        |         "from_name": "Birdman616",
        |         "to_id": "23161357",
        |         "to_name": "LIRIK",
        |         "followed_at": "2017-08-22T22:55:04Z"
        |      }
        |   ],
        |   "pagination":{
        |     "cursor": "eyJiIjpudWxsLCJhIjoiMTUwMzQ0MTc3NjQyNDQyMjAwMCJ9"
        |   }
        |}
        |""".stripMargin

    "Properly deserialize a twitchReply User" in {
      import TwitchReply._
      val actorSystem = ActorSystem("m")
      implicit val mat: Materializer = ActorMaterializer()(actorSystem)
      Await.result(Unmarshal(defaultUserReply).to[TwitchReply[User]].map {
        replyUser =>
          replyUser.data
            .map { users =>
              users should contain(
                User(
                  "affiliate",
                  "I am a stream hobbyist. Trying to put enough time to put something that is worth watching with decent quality. I really enjoy interacting with viewers.Twitter: @igrcndd",
                  "igrcndd",
                  None,
                  "55407179",
                  "igrcndd",
                  "https://static-cdn.jtvnw.net/jtv_user_pictures/d71aedc3628eae34-channel_offline_image-1920x1080.jpeg",
                  "https://static-cdn.jtvnw.net/jtv_user_pictures/42c29bc3-8267-490c-84c8-5d695b16a62f-profile_image-300x300.png",
                  "",
                  6420
                )
              )
            }
            .getOrElse(fail("No users found"))
      }, 15 seconds)
    }

    "Properly deserialize a twitchReply Follow" in {
      import TwitchReply._
      val actorSystem = ActorSystem("m")
      implicit val mat: Materializer = ActorMaterializer()(actorSystem)
      Await.result(
        Unmarshal(defaultFollowersReply).to[TwitchReply[Follow]].map {
          replyFollow =>
            {
              replyFollow.data.map { follow =>
                follow should contain(
                  Follow(
                    "171003792",
                    "IIIsutha067III",
                    "23161357",
                    "LIRIK",
                    "2017-08-22T22:55:24Z"
                  )
                )

              }
            }.getOrElse(fail("No follows found"))
            replyFollow.total should contain(12345L)
            replyFollow.pagination should contain(
              Pagination("eyJiIjpudWxsLCJhIjoiMTUwMzQ0MTc3NjQyNDQyMjAwMCJ9")
            )
        },
        15 seconds
      )
    }
  }
}

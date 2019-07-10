package services.twitch

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import hazzlenut.services.twitch.TokenHolder.{AskAccessToken, ReplyAccessToken}
import hazzlenut.services.twitch.model.User
import hazzlenut.services.twitch.{AccessToken, TwitchClient, UserInfo}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import utils.{TestIO, UserGen}
import utils.TestIO._
import cats.implicits._
import hazzlenut.services.twitch.UserInfo.{ProvideUser, RetrieveUser}

import concurrent.duration._

class UserInfoSpec extends TestKit(ActorSystem("UserInfoSpec"))
  with WordSpecLike
  with ImplicitSender
  with Matchers
  with BeforeAndAfterAll {

  val dummyAccessToken = AccessToken("", "", 10, None)

  "UserInfo" should {
    "Ask for the token when it starts" in {
      val probe = TestProbe()

      val userInfo = system.actorOf(UserInfo.props(probe.ref))

      probe.expectMsg(AskAccessToken)
    }

    "Ask for the user when it receives the token" in {
      val probe = TestProbe()

      val user = UserGen.getSample()

      implicit val twitchClient = TestIO.createTwitchClient(userReturn = TestIO[User](Either.right(user)))

      val userInfo = system.actorOf(UserInfo.props(probe.ref))

      probe.expectMsg(AskAccessToken)

      userInfo.tell(ReplyAccessToken(dummyAccessToken), probe.ref)

      awaitAssert {
        userInfo.tell(RetrieveUser, probe.ref)
        probe.expectMsg(10 millis, ProvideUser(user))
      }
    }
  }

}

package services.twitch

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import hazzlenut.services.twitch.TokenHolder.{AskAccessToken, ReplyAccessToken, TokenExpiredNeedNew}
import hazzlenut.services.twitch.model.User
import hazzlenut.services.twitch.{AccessToken, TwitchClient, UserInfo}
import org.scalatest.{AsyncWordSpecLike, BeforeAndAfterAll, Matchers, WordSpecLike}
import utils.{AccessTokenGen, TestIO, UserGen}
import utils.TestIO._
import cats.implicits._
import hazzlenut.errors.HazzlenutError.UnableToAuthenticate
import hazzlenut.services.twitch.UserInfo.{ProvideUser, RetrieveUser}

import concurrent.duration._

class UserInfoSpec extends TestKit(ActorSystem("UserInfoSpec"))
  with AsyncWordSpecLike
  with ImplicitSender
  with Matchers
  with BeforeAndAfterAll {

  val dummyAccessToken = AccessTokenGen.sample()

  "UserInfo" should {
    "Ask for the token when it starts" in {
      val probe = TestProbe()

      val userInfo = system.actorOf(UserInfo.props(probe.ref))

      probe.expectMsg(AskAccessToken)
      succeed
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
        succeed
      }
    }

    "Ask for the user and get UnAuthenticated" in {
      val probe = TestProbe()

      val user = UserGen.getSample()

      implicit val twitchClient = TestIO.createTwitchClient(userReturn = TestIO[User](Either.left(UnableToAuthenticate)))

      val userInfo = system.actorOf(UserInfo.props(probe.ref))

      probe.expectMsg(AskAccessToken)

      userInfo.tell(ReplyAccessToken(dummyAccessToken), probe.ref)
      probe.expectMsg(TokenExpiredNeedNew)
      succeed
    }

    "Ask for the user and get UnAuthenticated and Recover" in {
      val probe = TestProbe()

      val user = UserGen.getSample()

      val it = Iterator(TestIO[User](Either.left(UnableToAuthenticate)), TestIO[User](Either.right(user)))

      implicit val twitchClient = TestIO.createTwitchClient(userReturn = { it.next })

      val userInfo = system.actorOf(UserInfo.props(probe.ref))

      probe.expectMsg(AskAccessToken)

      userInfo.tell(ReplyAccessToken(dummyAccessToken), probe.ref)
      probe.expectMsg(TokenExpiredNeedNew)

      userInfo.tell(ReplyAccessToken(dummyAccessToken), probe.ref)

      awaitAssert {
        userInfo.tell(RetrieveUser, probe.ref)
        probe.expectMsg(10 millis, ProvideUser(user))
        succeed
      }
    }
  }

}

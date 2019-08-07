package services.twitch

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import cats.{Id, Monad}
import cats.implicits._
import hazzlenut.errors.HazzlenutError.{UnableToAuthenticate, UnableToConnect}
import hazzlenut.handler.TwitchClientHandler
import hazzlenut.services.twitch.TokenHolder.{AskAccessToken, ReplyAccessToken, TokenExpiredNeedNew}
import hazzlenut.services.twitch.{TwitchClient, UserInfo}
import hazzlenut.services.twitch.UserInfo.{ProvideUser, RetrieveUser}
import hazzlenut.services.twitch.model.User
import hazzlenut.util.{HttpClient, LogProvider}
import org.scalatest.{AsyncWordSpecLike, BeforeAndAfterAll, Matchers}
import utils.TestIO._
import utils.{AccessTokenGen, TestIO, UserGen}

import scala.concurrent.duration._

class UserInfoSpec
    extends TestKit(ActorSystem("UserInfoSpec"))
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

      implicit val twitchClient =
        TestIO.createTwitchClient(userReturn = TestIO[User](Either.right(user)))

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

      implicit val twitchClient = TestIO.createTwitchClient(
        userReturn = TestIO[User](Either.left(UnableToAuthenticate))
      )

      val userInfo = system.actorOf(UserInfo.props(probe.ref))

      probe.expectMsg(AskAccessToken)

      userInfo.tell(ReplyAccessToken(dummyAccessToken), probe.ref)
      probe.expectMsg(TokenExpiredNeedNew)
      succeed
    }

    "Ask for the user and get UnAuthenticated and Recover" in {
      val probe = TestProbe()

      val user = UserGen.getSample()

      val it = Iterator(
        TestIO[User](Either.left(UnableToAuthenticate)),
        TestIO[User](Either.right(user))
      )

      implicit val twitchClient = TestIO.createTwitchClient(userReturn = {
        it.next
      })

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

    "Log error when failing to get user" in {
      val probe = TestProbe()

      implicit val twitchClient = TestIO.createTwitchClient(
        userReturn = TestIO[User](Either.left(UnableToConnect))
      )
      val applyOnLogging = new (~>[Id]) {
        override def apply[A, B](a: Id[A], b: Id[B]): (A, B) =
          (a, b)
      }

      implicit val providerLog: LogProvider[TestIO] =
        createLogProvider(applyOnLogging)

      val userInfo = system.actorOf(
        UserInfo.props(probe.ref)(
          implicitly[TwitchClientHandler[TestIO]],
          implicitly[TwitchClient[TestIO]],
          implicitly[HttpClient[TestIO]],
          providerLog,
          implicitly[Monad[TestIO]]
        )
      )

      probe.expectMsg(AskAccessToken)

      userInfo.tell(ReplyAccessToken(dummyAccessToken), probe.ref)
      probe.expectMsg( AskAccessToken)

      succeed
    }
  }

}

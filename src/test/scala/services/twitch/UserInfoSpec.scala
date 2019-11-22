package services.twitch

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import cats.implicits._
import cats.{Id, Monad}
import hazzlenut.errors.HazzlenutError.{UnableToAuthenticate, UnableToConnect}
import hazzlenut.handler.TwitchClientHandler
import hazzlenut.services.twitch.actor.TokenGuardian.ApplicationStarted
import hazzlenut.services.twitch.actor.TokenHolder.{AskAccessToken, ReplyAccessToken, TokenExpiredNeedNew}
import hazzlenut.services.twitch.actor.UserInfo
import hazzlenut.services.twitch.actor.UserInfo.{ProvideUser, RetrieveUser}
import hazzlenut.services.twitch.actor.adapter.TwitchClient
import hazzlenut.services.twitch.actor.helper.Executor
import hazzlenut.services.twitch.model.User
import hazzlenut.util.Semantic._
import hazzlenut.util.{HttpClient, LogProvider, UnmarshallerEntiy}
import log.effect.LogLevel
import log.effect.LogLevels.Debug
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

    def createUserInfoAndStart[F[_]: TwitchClientHandler: TwitchClient: HttpClient: LogProvider: Monad: UnmarshallerEntiy: Executor](
      actorRef: ActorRef
    ): ActorRef =
      system.actorOf(UserInfo.props[F](actorRef)).tap(_ ! ApplicationStarted)

    "Ask for the token when it starts" in {
      val probe = TestProbe()

      createUserInfoAndStart(probe.ref)

      probe.expectMsg(AskAccessToken)
      succeed
    }

    "Ask for the user when it receives the token" in {
      val probe = TestProbe()

      val user = UserGen.getSample()

      implicit val twitchClient =
        TestIO.createTwitchClient(userReturn = TestIO[User](Either.right(user)))

      val userInfo = createUserInfoAndStart(probe.ref)

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

      implicit val twitchClient = TestIO.createTwitchClient(
        userReturn = TestIO[User](Either.left(UnableToAuthenticate))
      )

      val userInfo = createUserInfoAndStart(probe.ref)

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

      val userInfo = createUserInfoAndStart(probe.ref)

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
      val expected =
        "Failure on call to get User with error Throwable: message: None, stackTrace: hazzlenut.errors.HazzlenutError$UnableToConnect$"

      val probe = TestProbe()

      implicit val twitchClient = TestIO.createTwitchClient(
        userReturn = TestIO[User](Either.left(UnableToConnect))
      )
      val applyOnLogging = new (~>[Id]) {
        var written: (String, LogLevel) = ("", Debug)

        override def apply[A, B](a: Id[A], b: Id[B])(
          implicit ev2: B <:< LogLevel
        ): (A, B) = {
          written = (a.asInstanceOf[Id[String]], b)
          (a, b)
        }
      }

      implicit val providerLog: LogProvider[TestIO] =
        createLogProvider(applyOnLogging)

      val userInfo = createUserInfoAndStart(probe.ref)(
        implicitly[TwitchClientHandler[TestIO]],
        implicitly[TwitchClient[TestIO]],
        implicitly[HttpClient[TestIO]],
        providerLog,
        implicitly[Monad[TestIO]],
        implicitly[UnmarshallerEntiy[TestIO]],
        implicitly[Executor[TestIO]]
      )

      probe.expectMsg(AskAccessToken)

      userInfo.tell(ReplyAccessToken(dummyAccessToken), probe.ref)
      probe.expectMsg(AskAccessToken)
      assert(applyOnLogging.written._1 contains expected)
      assert(applyOnLogging.written._2 == Debug)
    }
  }

}

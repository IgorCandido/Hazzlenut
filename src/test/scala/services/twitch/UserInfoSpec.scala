package services.twitch

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import cats.{Id, Monad}
import cats.implicits._
import hazzlenut.errors.HazzlenutError.{UnableToAuthenticate, UnableToConnect}
import hazzlenut.handler.TwitchClientHandler
import hazzlenut.services.twitch.TokenHolder.{
  AskAccessToken,
  ReplyAccessToken,
  TokenExpiredNeedNew
}
import hazzlenut.services.twitch.{TwitchClient, UserInfo}
import hazzlenut.services.twitch.UserInfo.{ProvideUser, RetrieveUser}
import hazzlenut.services.twitch.model.User
import hazzlenut.util.{HttpClient, LogProvider}
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
      val expected =
        """Failure on call to get User with error Throwable: message: Test, stackTrace: hazzlenut.errors.HazzlenutError$UnableToConnect$: Test
	at hazzlenut.errors.HazzlenutError$UnableToConnect$.<clinit>(HazzlenutError.scala)
	at services.twitch.UserInfoSpec.$anonfun$new$12(UserInfoSpec.scala:141)
	at utils.TestIOTwitchClient$$anon$9.user(TestIO.scala:330)
	at utils.TestIOTwitchClient$$anon$9.user(TestIO.scala:316)
	at utils.TestIOTwitchClient$$anon$11.retrieveUser(TestIO.scala:390)
	at hazzlenut.handler.TwitchClientHandler$dsl$.retrieveUser(TwitchClientHandler.scala:32)
	at hazzlenut.services.twitch.UserInfo$$anonfun$waitingForToken$1.applyOrElse(UserInfo.scala:53)
	at akka.actor.Actor.aroundReceive(Actor.scala:539)
	at akka.actor.Actor.aroundReceive$(Actor.scala:537)
	at hazzlenut.services.twitch.UserInfo.aroundReceive(UserInfo.scala:33)
	at akka.actor.ActorCell.receiveMessage(ActorCell.scala:612)
	at akka.actor.ActorCell.invoke(ActorCell.scala:581)
	at akka.dispatch.Mailbox.processMailbox(Mailbox.scala:268)
	at akka.dispatch.Mailbox.run(Mailbox.scala:229)
	at akka.dispatch.Mailbox.exec(Mailbox.scala:241)
	at akka.dispatch.forkjoin.ForkJoinTask.doExec(ForkJoinTask.java:260)
	at akka.dispatch.forkjoin.ForkJoinPool$WorkQueue.runTask(ForkJoinPool.java:1339)
	at akka.dispatch.forkjoin.ForkJoinPool.runWorker(ForkJoinPool.java:1979)
	at akka.dispatch.forkjoin.ForkJoinWorkerThread.run(ForkJoinWorkerThread.java:107)
"""

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
      assert(applyOnLogging.written._1 == expected)
      assert(applyOnLogging.written._2 == Debug)
    }
  }

}

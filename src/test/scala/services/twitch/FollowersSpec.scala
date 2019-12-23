package services.twitch

import akka.actor.SupervisorStrategy.{Restart, Stop}
import akka.actor.{Actor, ActorRef, ActorSystem, Kill, OneForOneStrategy, PoisonPill, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import cats.MonadError
import hazzlenut.errors.HazzlenutError
import hazzlenut.services.twitch.actor.{Followers, TokenGuardian}
import hazzlenut.services.twitch.actor.Followers.Follower
import hazzlenut.services.twitch.actor.TokenGuardian.Message.{RequireService, ServiceProvide}
import hazzlenut.services.twitch.actor.TokenGuardian.ServiceType
import hazzlenut.services.twitch.actor.TokenHolder.{AskAccessToken, ReplyAccessToken}
import hazzlenut.services.twitch.actor.UserInfo.{ProvideUser, RetrieveUser}
import hazzlenut.services.twitch.actor.adapter.TwitchClient
import hazzlenut.services.twitch.actor.model.CommonMessages.{ApplicationStarted, KillService}
import hazzlenut.services.twitch.adapters.AccessToken
import hazzlenut.services.twitch.helper.CommonReferences
import hazzlenut.services.twitch.model.{Follow, Pagination, TwitchSeqWithMeta, User}
import org.scalatest.{Matchers, WordSpecLike}
import utils.{AccessTokenGen, FollowersReplyGen, TestIO, UserGen}
import utils.TestIO._
import cats.implicits._
import hazzlenut.services.twitch.actor.model.CommonMessages.SupervisorThrowables.ProperlyKilled
import services.twitch.FollowersSpec.ActorT.WasItProperlyKilled

import scala.collection.mutable
import scala.concurrent.duration._

object FollowersSpec {

  object ActorT {
    final case class CreateFollowers(props: Props)
    final case object WasItProperlyKilled
  }

  class ActorT extends Actor {
    import ActorT._
    var properlyKilled = false

    override def receive: Receive = {
      case CreateFollowers(props) => sender ! createFollowers(props)
      case WasItProperlyKilled => sender ! properlyKilled
    }

    def createFollowers(props: Props): ActorRef =
      context.actorOf(props)

    override val supervisorStrategy =
      OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
        case _: ProperlyKilled => {
          properlyKilled = true
          Stop
        }
        case _              => Restart
      }
  }

  final case class FollowerCall(cursor: Option[String])

  object implicits {
    implicit def defaultTestTwitchClient() = new TwitchClient[TestIO] {
      var followersCalledTimes = 0
      val cursorValues = mutable.Buffer(
        "first",
        "second",
        "third",
        "forth",
        "fifth",
        "first",
        "second",
        "third",
        "forth",
        "fifth"
      )
      val calls = mutable.Buffer.empty[FollowerCall]

      override def fromOption[Out](
        optionOf: Option[Out],
        hazzlenutError: HazzlenutError
      )(implicit monadError: MonadError[TestIO, HazzlenutError]): TestIO[Out] =
        ???

      override def user(accessToken: AccessToken)(
        implicit commonReferences: CommonReferences[TestIO],
        monadErrorThrowable: MonadError[TestIO, HazzlenutError]
      ): TestIO[User] = ???

      override def followers(accessToken: AccessToken,
                             userId: String,
                             cursor: Option[String])(
        implicit commonReferences: CommonReferences[TestIO],
        monadErrorThrowable: MonadError[TestIO, HazzlenutError]
      ): TestIO[TwitchSeqWithMeta[Follow]] = {
        followersCalledTimes += 1
        calls += FollowerCall(cursor)
        TestIO(
          Right(
            FollowersReplyGen.getSample
              .copy(pagination = Pagination(cursorValues.remove(0)))
          )
        )
      }
    }
  }
}

class FollowersSpec
    extends TestKit(ActorSystem("FollowersSpec"))
    with WordSpecLike
    with Matchers {
  import FollowersSpec._

  "Merge followers" should {
    final case class FollowersMergeTestCase(testName: String,
                                            existingFollowers: Seq[Follower],
                                            newFollowers: Seq[Follower],
                                            expectedFollowers: Seq[Follower])

    List(
      FollowersMergeTestCase(
        "merge Seq of Followers",
        Seq(Follower("testA", "1"), Follower("testB", "2")),
        Seq.empty,
        Seq(Follower("testA", "1"), Follower("testB", "2"))
      ),
      FollowersMergeTestCase(
        "merge Seq of new Follower",
        Seq(Follower("testA", "1"), Follower("testB", "2")),
        Seq(Follower("testC", "3")),
        Seq(
          Follower("testA", "1"),
          Follower("testB", "2"),
          Follower("testC", "3")
        )
      ),
      FollowersMergeTestCase(
        "merge Seq with repeated Follower",
        Seq(Follower("testA", "1"), Follower("testB", "2")),
        Seq(Follower("testA", "1")),
        Seq(Follower("testA", "1"), Follower("testB", "2"))
      ),
      FollowersMergeTestCase(
        "merge Seq with repeated Follower and different follow date",
        Seq(Follower("testA", "1"), Follower("testB", "2")),
        Seq(Follower("testA", "2")),
        Seq(Follower("testA", "1"), Follower("testB", "2"))
      )
    ).foreach { testCase =>
      testCase.testName in {
        Followers.mergeFollowers(
          testCase.existingFollowers,
          testCase.newFollowers
        ) should equal(testCase.expectedFollowers)
      }
    }
  }

  "Followers" should {
    def startFollowers(followers: ActorRef,
                       userInfo: TestProbe,
                       tokenGuardian: TestProbe,
                       tokenHolder: TestProbe) = {
      followers ! ApplicationStarted
      tokenGuardian.expectMsg(RequireService(ServiceType.UserInfo))

      followers.tell(
        ServiceProvide(ServiceType.UserInfo, userInfo.ref),
        tokenGuardian.ref
      )
      tokenHolder.expectMsg(AskAccessToken)

      followers.tell(ReplyAccessToken(AccessTokenGen.sample), tokenHolder.ref)
      userInfo.expectMsg(RetrieveUser)

      followers.tell(ProvideUser(UserGen.getSample), userInfo.ref)
    }

    "When started ask for an userInfo, accessToken, user and followers" in {
      implicit lazy val twitchClient = implicits.defaultTestTwitchClient()

      implicit val system = ActorSystem()
      val tokenGuardian = TestProbe()
      val tokenHolder = TestProbe()
      val userInfo = TestProbe()

      val followers =
        system.actorOf(
          Followers
            .props[TestIO](tokenGuardian.ref, tokenHolder.ref, 10 minutes)
        )

      startFollowers(followers, userInfo, tokenGuardian, tokenHolder)

      awaitAssert(
        twitchClient.followersCalledTimes should equal(1),
        max = 1 seconds
      )
      //TODO: Eventually look into the stream producer to check if the followers were properly registered
    }

    "Polling of followers with correct values for cursor" in {
      implicit lazy val twitchClient = implicits.defaultTestTwitchClient()

      implicit val system = ActorSystem()
      val tokenGuardian = TestProbe()
      val tokenHolder = TestProbe()
      val userInfo = TestProbe()

      val followers =
        system.actorOf(
          Followers
            .props[TestIO](tokenGuardian.ref, tokenHolder.ref, 300 milliseconds)
        )

      startFollowers(followers, userInfo, tokenGuardian, tokenHolder)

      awaitAssert(
        twitchClient.calls should equal(
          mutable.Buffer(
            FollowerCall(None),
            FollowerCall("first".some),
            FollowerCall("second".some),
            FollowerCall("third".some),
            FollowerCall("forth".some),
            FollowerCall("fifth".some)
          )
        ),
        max = 1700 milliseconds
      )
      //TODO: Eventually look into the stream producer to check if the followers were properly registered
    }

    "Killing the Followers actor and restarting with state should trigger GetUserInfo" in {
      implicit lazy val twitchClient = implicits.defaultTestTwitchClient()

      implicit val system = ActorSystem()
      val tokenGuardian = TestProbe()
      val tokenHolder = TestProbe()
      val userInfo = TestProbe()

      val a = system.actorOf(Props(new ActorT))
      a.tell(
        ActorT.CreateFollowers(
          Followers
            .props[TestIO](tokenGuardian.ref, tokenHolder.ref, 500 milliseconds)
        ),
        testActor
      )

      val followers: ActorRef = receiveOne(1 second).asInstanceOf[ActorRef]

      startFollowers(followers, userInfo, tokenGuardian, tokenHolder)

      Thread.sleep(1000)

      followers ! Kill

      tokenGuardian.expectMsg(1 seconds, RequireService(ServiceType.UserInfo))
    }

    "Properly killing the Followers actor and restarting with state should not trigger GetUserInfo" in {
      implicit lazy val twitchClient = implicits.defaultTestTwitchClient()

      implicit val system = ActorSystem()
      val tokenGuardian = TestProbe()
      val tokenHolder = TestProbe()
      val userInfo = TestProbe()

      val a = system.actorOf(Props(new ActorT))
      a.tell(
        ActorT.CreateFollowers(
          Followers
            .props[TestIO](tokenGuardian.ref, tokenHolder.ref, 500 milliseconds)
        ),
        testActor
      )

      val followers: ActorRef = receiveOne(1 second).asInstanceOf[ActorRef]

      startFollowers(followers, userInfo, tokenGuardian, tokenHolder)

      Thread.sleep(1000)

      followers ! KillService

      tokenGuardian.expectNoMessage(1 seconds)
    }

    "Properly killing the Followers actor, restarting and initializing it with state should not trigger GetUserInfo" in {
      implicit lazy val twitchClient = implicits.defaultTestTwitchClient()

      implicit val system = ActorSystem()
      val tokenGuardian = TestProbe()
      val tokenHolder = TestProbe()
      val userInfo = TestProbe()

      val a = system.actorOf(Props(new ActorT))
      a.tell(
        ActorT.CreateFollowers(
          Followers
            .props[TestIO](tokenGuardian.ref, tokenHolder.ref, 500 milliseconds)
        ),
        testActor
      )

      val followers: ActorRef = receiveOne(1 second).asInstanceOf[ActorRef]

      startFollowers(followers, userInfo, tokenGuardian, tokenHolder)

      Thread.sleep(1000)

      followers ! KillService

      tokenGuardian.expectNoMessage(1 seconds)

      followers.tell(ApplicationStarted, tokenGuardian.ref)

      tokenGuardian.expectNoMessage(1 seconds)

      a.tell(WasItProperlyKilled, testActor)
      expectMsg(true)
    }
  }
}

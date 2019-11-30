package services.twitch

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import cats.MonadError
import hazzlenut.errors.HazzlenutError
import hazzlenut.services.twitch.actor.Followers
import hazzlenut.services.twitch.actor.Followers.Follower
import hazzlenut.services.twitch.actor.TokenGuardian.Message.{
  RequireService,
  ServiceProvide
}
import hazzlenut.services.twitch.actor.TokenGuardian.ServiceType
import hazzlenut.services.twitch.actor.TokenHolder.{
  AskAccessToken,
  ReplyAccessToken
}
import hazzlenut.services.twitch.actor.UserInfo.{ProvideUser, RetrieveUser}
import hazzlenut.services.twitch.actor.adapter.TwitchClient
import hazzlenut.services.twitch.actor.model.CommonMessages.ApplicationStarted
import hazzlenut.services.twitch.adapters.AccessToken
import hazzlenut.services.twitch.helper.CommonReferences
import hazzlenut.services.twitch.model.{
  Follow,
  Pagination,
  TwitchSeqWithMeta,
  User
}
import org.scalatest.{Matchers, WordSpecLike}
import utils.{AccessTokenGen, FollowersReplyGen, TestIO, UserGen}
import utils.TestIO._
import cats.implicits._

import scala.collection.mutable
import scala.concurrent.duration._

class FollowersSpec
    extends TestKit(ActorSystem("FollowersSpec"))
    with WordSpecLike
    with Matchers {
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
      implicit val system = ActorSystem()
      val tokenGuardian = TestProbe()
      val tokenHolder = TestProbe()
      val userInfo = TestProbe()

      implicit val twitchClient = new TwitchClient[TestIO] {
        var followersCalledTimes = 0

        override def fromOption[Out](optionOf: Option[Out],
                                     hazzlenutError: HazzlenutError)(
          implicit monadError: MonadError[TestIO, HazzlenutError]
        ): TestIO[Out] = ???

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
          TestIO(Right(FollowersReplyGen.getSample))
        }
      }

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
      implicit val system = ActorSystem()
      val tokenGuardian = TestProbe()
      val tokenHolder = TestProbe()
      val userInfo = TestProbe()
      final case class FollowerCall(cursor: Option[String])

      implicit val twitchClient = new TwitchClient[TestIO] {

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

        override def fromOption[Out](optionOf: Option[Out],
                                     hazzlenutError: HazzlenutError)(
          implicit monadError: MonadError[TestIO, HazzlenutError]
        ): TestIO[Out] = ???

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
          calls += FollowerCall(cursor)
          TestIO(
            Right(
              FollowersReplyGen.getSample
                .copy(pagination = Pagination(cursorValues.remove(0)))
            )
          )
        }
      }

      val followers =
        system.actorOf(
          Followers
            .props[TestIO](tokenGuardian.ref, tokenHolder.ref, 200 milliseconds)
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

  }
}

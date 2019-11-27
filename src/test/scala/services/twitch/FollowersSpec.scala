package services.twitch

import hazzlenut.services.twitch.actor.Followers
import hazzlenut.services.twitch.actor.Followers.Follower
import org.scalatest.{Matchers, WordSpec}

class FollowersSpec extends WordSpec with Matchers {
  "Followers" should {
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
      FollowersMergeTestCase("merge Seq of new Follower",
        Seq(Follower("testA", "1"), Follower("testB", "2")),
        Seq(Follower("testC", "3")),
        Seq(Follower("testA", "1"), Follower("testB", "2"), Follower("testC", "3"))
        ),
      FollowersMergeTestCase("merge Seq with repeated Follower",
        Seq(Follower("testA", "1"), Follower("testB", "2")),
        Seq(Follower("testA", "1")),
        Seq(Follower("testA", "1"), Follower("testB", "2"))
      ),
      FollowersMergeTestCase("merge Seq with repeated Follower and different follow date",
        Seq(Follower("testA", "1"), Follower("testB", "2")),
        Seq(Follower("testA", "2")),
        Seq(Follower("testA", "1"), Follower("testB", "2"))
      )
    ).foreach{ testCase => testCase.testName in {
      Followers.mergeFollowers(testCase.existingFollowers, testCase.newFollowers) should equal(
        testCase.expectedFollowers
      )
    }}
  }
}

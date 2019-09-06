package utils

import hazzlenut.services.twitch.model.{FollowersReply, User}
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen

object FollowersReplyGen {
  def apply(): Gen[FollowersReply] =
    for{
      _cursor <- arbitrary[String]
      _total <- arbitrary[String]
      followers <- Gen.containerOf[Seq, User](UserGen())
    } yield FollowersReply(_cursor, _total, followers)

  def getSample(): FollowersReply = apply().sample.get
}

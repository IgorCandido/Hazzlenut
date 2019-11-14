package utils

import hazzlenut.services.twitch.model.Follow
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen

object FollowersReplyGen {
  def apply(): Gen[Seq[Follow]] = Gen.sized { size =>
    (for {
      from_id <- arbitrary[String]
      from_name <- arbitrary[String]
      to_id <- arbitrary[String]
      to_name <- arbitrary[String]
      followed_at <- arbitrary[String]
    } yield Follow(from_id, from_name, to_id,to_name, followed_at)).flatMap(
      Gen.listOfN(size, _)
    )
  }

  def getSample(): Seq[Follow] = apply().sample.get
}

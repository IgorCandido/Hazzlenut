package utils

import org.scalacheck.Gen
import org.scalacheck._
import Gen._
import Arbitrary.arbitrary
import hazzlenut.services.twitch.model.User

/*
val defaultUser = User(
broadcaster_type = "",
description = "",
display_name = "",
email = None,
id = "",
login = "",
offline_image_url = "",
profile_image_url = "",
`type` = "",
view_count = 0
)
 */

object UserGen {
  def apply(): User = (for {
    broadcaster_type <- arbitrary[String]
    description <- arbitrary[String]
    display_name <- arbitrary[String]
    email <- arbitrary[Option[String]]
    id <- arbitrary[String]
    login <- arbitrary[String]
    offline_image_url <- arbitrary[String]
    profile_image_url <- arbitrary[String]
    typeValue <- arbitrary[String]
    view_count <- arbitrary[Int]
  } yield
    User(
      broadcaster_type,
      description,
      display_name,
      email,
      id,
      login,
      offline_image_url,
      profile_image_url,
      typeValue,
      view_count
    )).sample.get
}

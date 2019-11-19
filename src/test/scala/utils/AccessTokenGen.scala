package utils

import hazzlenut.services.twitch.adapters.AccessToken
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen

/*
   accessToken: String,
   tokenType: String,
   expiresIn: Int, // Number of seconds
   refreshToken: Option[String]

 */

object AccessTokenGen {
  def apply(): Gen[AccessToken] =
    for {
      accessToken <- arbitrary[String]
      tokenType <- arbitrary[String]
      expiresIn <- arbitrary[Int]
      refresh <- arbitrary[Option[String]]
    } yield AccessToken(accessToken, tokenType, expiresIn, refresh)

  def sample(): AccessToken = apply().sample.get
}

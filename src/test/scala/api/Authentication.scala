package api

import akka.actor.ActorSystem
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.testkit.{TestKit, TestProbe}
import hazzlenut.api.Authentication
import hazzlenut.services.twitch.AccessToken
import org.scalatest.{Matchers, WordSpecLike}
import utils.TestIO
import cats.implicits._
import hazzlenut.services.twitch.TokenGuardian.Authenticated

import scala.concurrent.Future

class Authentication
    extends WordSpecLike
    with Matchers
    with ScalatestRouteTest {

  "Authentication route" should {
    "Send AccessToken to TokenGuardian when oauth reauthentication is finished" in {
      val tokenGuardianProbe = TestProbe()
      val accessToken = AccessToken(
        accessToken = "authed",
        tokenType = "",
        expiresIn = 200,
        refreshToken = "242adas".some
      )

      implicit val tokenGuardianRef = tokenGuardianProbe.ref
      implicit val authenticationHander =
        TestIO.authenticationHandlerWithValues(
          obtainOAuthValue = Future.successful(accessToken)
        )

      val authenticationRoute = Authentication.route

      Get("/oauth/reply?code=testCode") ~> authenticationRoute ~> check{
        handled should ===(true)
      }

      tokenGuardianProbe.expectMsg(Authenticated(accessToken))

    }
  }

}

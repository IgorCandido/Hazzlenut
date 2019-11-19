package api

import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.testkit.TestProbe
import cats.implicits._
import hazzlenut.api.Authentication
import hazzlenut.services.twitch.actor.TokenGuardian.Authenticated
import hazzlenut.services.twitch.adapters.AccessToken
import hazzlenut.util.HttpClient
import org.scalatest.{Matchers, WordSpecLike}
import utils.TestIO

import scala.concurrent.Future

class AuthenticationSpec
    extends WordSpecLike
    with Matchers
    with ScalatestRouteTest {

  "Authentication route" should {
    "Send AccessToken to TokenGuardian when oauth reauthentication is finished" in {
      val tokenGuardianProbe = TestProbe()
      val userInfoProbe = TestProbe()
      val accessToken = AccessToken(
        accessToken = "authed",
        tokenType = "",
        expiresIn = 200,
        refreshToken = "242adas".some
      )

      implicit val authenticationHander =
        TestIO.authenticationHandlerWithValues(
          obtainOAuthValue = Future.successful(accessToken)
        )

      implicit val twitchClient = TestIO.twitchClient
      implicit val twitchHandler = TestIO.twitchHandler
      implicit val httpClient: HttpClient[TestIO] =
        TestIO.defaultEmptyResponseHttpClient

      val authenticationRoute =
        Authentication.route(tokenGuardianProbe.ref, userInfoProbe.ref)

      Get("/oauth/reply?code=testCode") ~> authenticationRoute ~> check {
        handled should ===(true)
      }

      tokenGuardianProbe.expectMsg(Authenticated(accessToken))

    }
  }

}

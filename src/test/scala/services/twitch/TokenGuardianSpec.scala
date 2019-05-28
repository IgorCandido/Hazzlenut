package services.twitch

import akka.actor.{ActorContext, ActorRef, ActorSystem}
import akka.testkit.{TestKit, TestProbe}
import hazzlenut.handler.AuthenticationHandler
import hazzlenut.services.twitch.{AccessToken, TokenGuardian, TokenHolder, TokenHolderInitializer}
import org.scalatest.{Matchers, WordSpecLike}
import utils.TestIO
import cats.implicits._
import hazzlenut.services.twitch.TokenGuardian.Authenticated
import hazzlenut.services.twitch.TokenHolder.AskAccessToken

import scala.concurrent.duration._

class TokenGuardianSpec
    extends TestKit(ActorSystem("TokenGuardianSpec"))
    with WordSpecLike
    with Matchers {

  "Token Guardian" should {
    "Create token holder when it starts" in {
      val accessToken = AccessToken(
        accessToken = "Token",
        tokenType = "bearer",
        expiresIn = 3500,
        refreshToken = "refreshToken".some
      )

      val tokenHolderProbe = TestProbe()
      var authenticateUser = 0

      implicit val authenticationHandler =
        TestIO.authenticationHandlerWithValues(reAuthenticateParam = () => {
          authenticateUser += 1

          Either.right(Unit)
        })

      implicit val tokenHolderInitializer = new TokenHolderInitializer {
        var numberTokenHolderCreated = 0

        override def initializeTokenHolder(accessToken: AccessToken,
                                           self: ActorRef)(
          implicit context: ActorContext,
          authenticationHandler: AuthenticationHandler
        ): ActorRef = {
          numberTokenHolderCreated += 1
          tokenHolderProbe.ref
        }
      }

      val guardian = system.actorOf(TokenGuardian.props)

      guardian ! Authenticated(accessToken)

      awaitAssert(() => {
        tokenHolderInitializer.numberTokenHolderCreated should ===(1)
        authenticateUser should ===(1)
      }, 200 millis)
    }

    "Send messages queued whilst getting first access token" in {
      val accessToken = AccessToken(
        accessToken = "Token",
        tokenType = "bearer",
        expiresIn = 3500,
        refreshToken = "refreshToken".some
      )

      val tokenHolderProbe = TestProbe()

      implicit val authenticationHandler =
        TestIO.authenticationHandlerWithValues(reAuthenticateParam = () => {
          Either.right(Unit)
        })

      implicit val tokenHolderInitializer = new TokenHolderInitializer {
        var numberTokenHolderCreated = 0

        override def initializeTokenHolder(accessToken: AccessToken,
                                           self: ActorRef)(
                                            implicit context: ActorContext,
                                            authenticationHandler: AuthenticationHandler
                                          ): ActorRef = {
          numberTokenHolderCreated += 1
          tokenHolderProbe.ref
        }
      }

      val guardian = system.actorOf(TokenGuardian.props)

      val customer = TestProbe()

      guardian.tell(TokenHolder.AskAccessToken, customer.ref)
      customer.expectNoMessage(100 millis)

      guardian ! Authenticated(accessToken)

      awaitAssert{
        tokenHolderProbe.expectMsg(AskAccessToken)
        tokenHolderProbe.lastSender should ===(customer.ref)
      }
    }

    "Not send Token expired messages whilst getting first access token" in {
      val accessToken = AccessToken(
        accessToken = "Token",
        tokenType = "bearer",
        expiresIn = 3500,
        refreshToken = "refreshToken".some
      )

      val tokenHolderProbe = TestProbe()

      implicit val authenticationHandler =
        TestIO.authenticationHandlerWithValues(reAuthenticateParam = () => {
          Either.right(Unit)
        })

      implicit val tokenHolderInitializer = new TokenHolderInitializer {
        var numberTokenHolderCreated = 0

        override def initializeTokenHolder(accessToken: AccessToken,
                                           self: ActorRef)(
                                            implicit context: ActorContext,
                                            authenticationHandler: AuthenticationHandler
                                          ): ActorRef = {
          numberTokenHolderCreated += 1
          tokenHolderProbe.ref
        }
      }

      val guardian = system.actorOf(TokenGuardian.props)

      val customer = TestProbe()

      guardian.tell(TokenHolder.TokenExpiredNeedNew, customer.ref)
      customer.expectNoMessage(100 millis)

      guardian ! Authenticated(accessToken)

      tokenHolderProbe.expectNoMessage(500 millis)
    }
  }

}

package services.twitch

import akka.actor.{ActorContext, ActorRef, ActorSystem, PoisonPill}
import akka.testkit.{TestKit, TestProbe}
import hazzlenut.handler.AuthenticationHandler
import hazzlenut.services.twitch.{AccessToken, TokenGuardian, TokenHolder, TokenHolderInitializer}
import org.scalatest.{Matchers, WordSpecLike}
import utils.TestIO
import cats.implicits._
import hazzlenut.services.twitch.TokenGuardian.{ApplicationStarted, Authenticated}
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
      implicit val userInfoInitalizer = TestIO.userInfoInitializer(tokenHolderProbe.ref)
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
      guardian ! ApplicationStarted

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
      implicit val userInfoInitalizer = TestIO.userInfoInitializer(tokenHolderProbe.ref)

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
      guardian ! ApplicationStarted

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
      implicit val userInfoInitalizer = TestIO.userInfoInitializerWithActor(tokenHolderProbe.ref)

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
      guardian ! ApplicationStarted

      val customer = TestProbe()

      guardian.tell(TokenHolder.TokenExpiredNeedNew, customer.ref)
      customer.expectNoMessage(100 millis)

      guardian ! Authenticated(accessToken)

      tokenHolderProbe.expectNoMessage(500 millis)
    }

    "Receive token cannot be renewed" in {
      val accessToken = AccessToken(
        accessToken = "Token",
        tokenType = "bearer",
        expiresIn = 3500,
        refreshToken = "refreshToken".some
      )

      val tokenHolderProbe = TestProbe()
      implicit val userInfoInitalizer = TestIO.userInfoInitializer(tokenHolderProbe.ref)
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
      guardian ! ApplicationStarted

      val watcher = TestProbe()

      guardian ! Authenticated(accessToken)
      watcher.watch(tokenHolderProbe.ref)

      guardian.tell(TokenGuardian.CantRenewToken, tokenHolderProbe.ref)
      watcher.expectTerminated(tokenHolderProbe.ref)

      awaitAssert({
        authenticateUser should ===(2)
      }, 300 millis)
    }

    "Route messages to TokenHolder when working normally" in {
      val accessToken = AccessToken(
        accessToken = "Token",
        tokenType = "bearer",
        expiresIn = 3500,
        refreshToken = "refreshToken".some
      )

      val tokenHolderProbe = TestProbe()
      implicit val userInfoInitalizer = TestIO.userInfoInitializerWithActor(tokenHolderProbe.ref)
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
      guardian ! ApplicationStarted

      guardian ! Authenticated(accessToken)


      val customer = TestProbe()

      guardian.tell(TokenHolder.AskAccessToken, customer.ref)
      tokenHolderProbe.expectMsg(TokenHolder.AskAccessToken)
      tokenHolderProbe.lastSender should ===(customer.ref)
    }

    "Route tokenExpired to TokenHolder when working normally" in {
      val accessToken = AccessToken(
        accessToken = "Token",
        tokenType = "bearer",
        expiresIn = 3500,
        refreshToken = "refreshToken".some
      )

      val tokenHolderProbe = TestProbe()
      implicit val userInfoInitalizer = TestIO.userInfoInitializerWithActor(tokenHolderProbe.ref)
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
      guardian ! ApplicationStarted

      guardian ! Authenticated(accessToken)

      val customer = TestProbe()

      guardian.tell(TokenHolder.TokenExpiredNeedNew, customer.ref)
      tokenHolderProbe.expectMsg(TokenHolder.TokenExpiredNeedNew)
      tokenHolderProbe.lastSender should ===(customer.ref)
    }
  }
}

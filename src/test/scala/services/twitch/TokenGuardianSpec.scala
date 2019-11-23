package services.twitch

import akka.actor.{ActorContext, ActorRef, ActorSystem}
import akka.testkit.{TestKit, TestProbe}
import cats.Monad
import cats.implicits._
import hazzlenut.handler.{AuthenticationHandler, TwitchClientHandler}
import hazzlenut.services.twitch._
import hazzlenut.services.twitch.actor.TokenGuardian.Message.{ApplicationStarted, Authenticated, RequireService, ServiceProvide}
import hazzlenut.services.twitch.actor.TokenGuardian.ServiceInitializer
import hazzlenut.services.twitch.actor.TokenHolder.AskAccessToken
import hazzlenut.services.twitch.actor.adapter.TwitchClient
import hazzlenut.services.twitch.actor.helper.{TokenHolderInitializer, UserInfoInitializer}
import hazzlenut.services.twitch.actor.model.CommonMessages
import hazzlenut.services.twitch.actor.{TokenGuardian, TokenHolder}
import hazzlenut.services.twitch.adapters.AccessToken
import hazzlenut.util.{HttpClient, LogProvider}
import org.scalatest.{AsyncWordSpecLike, Matchers}
import utils.{AccessTokenGen, TestIO}
import hazzlenut.util.ZIORuntime._

import scala.concurrent.duration._

class TokenGuardianSpec
    extends TestKit(ActorSystem("TokenGuardianSpec"))
    with AsyncWordSpecLike
    with Matchers {

  "Token Guardian" should {
    "Create token holder when it starts" in {
      val accessToken = AccessTokenGen.sample()

      val tokenHolderProbe = TestProbe()
      var authenticateUser = 0

      implicit val authenticationHandler =
        TestIO.authenticationHandlerWithValues(reAuthenticateParam = () => {
          authenticateUser += 1

          Either.right(Unit)
        })

      implicit val tokenHolderInitializer = new TokenHolderInitializer[TestIO] {
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

      val guardian = system.actorOf(TokenGuardian.props[TestIO](Seq.empty))
      guardian ! ApplicationStarted

      guardian ! Authenticated(accessToken)

      awaitAssert({
        tokenHolderInitializer.numberTokenHolderCreated should ===(1)
        authenticateUser should ===(1)
      }, 200 millis)
    }

    "Send messages queued whilst getting first access token" in {
      val accessToken = AccessTokenGen.sample()

      val tokenHolderProbe = TestProbe()

      implicit val authenticationHandler =
        TestIO.authenticationHandlerWithValues(reAuthenticateParam = () => {
          Either.right(Unit)
        })

      implicit val tokenHolderInitializer = new TokenHolderInitializer[TestIO] {
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

      val guardian = system.actorOf(TokenGuardian.props[TestIO](Seq.empty))
      guardian ! ApplicationStarted

      val customer = TestProbe()

      guardian.tell(TokenHolder.AskAccessToken, customer.ref)
      customer.expectNoMessage(100 millis)

      guardian ! Authenticated(accessToken)

      awaitAssert {
        tokenHolderProbe.expectMsg(AskAccessToken)
        tokenHolderProbe.lastSender should ===(customer.ref)
      }
    }

    "Not send Token expired messages whilst getting first access token" in {
      val accessToken = AccessTokenGen.sample()

      val tokenHolderProbe = TestProbe()

      implicit val authenticationHandler =
        TestIO.authenticationHandlerWithValues(reAuthenticateParam = () => {
          Either.right(Unit)
        })

      implicit val tokenHolderInitializer = new TokenHolderInitializer[TestIO] {
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

      val guardian = system.actorOf(TokenGuardian.props[TestIO](Seq.empty))
      guardian ! ApplicationStarted

      val customer = TestProbe()

      guardian.tell(TokenHolder.TokenExpiredNeedNew, customer.ref)
      customer.expectNoMessage(100 millis)

      guardian ! Authenticated(accessToken)

      tokenHolderProbe.expectNoMessage(500 millis)

      // Hack
      succeed
    }

    "Receive token cannot be renewed" in {
      val accessToken = AccessTokenGen.sample()

      val tokenHolderProbe = TestProbe()
      var authenticateUser = 0

      implicit val authenticationHandler =
        TestIO.authenticationHandlerWithValues(reAuthenticateParam = () => {
          authenticateUser += 1
          Either.right(Unit)
        })

      implicit val tokenHolderInitializer = new TokenHolderInitializer[TestIO] {
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

      val guardian = system.actorOf(TokenGuardian.props[TestIO](Seq.empty))
      guardian ! ApplicationStarted

      val watcher = TestProbe()

      guardian ! Authenticated(accessToken)
      watcher.watch(tokenHolderProbe.ref)

      guardian.tell(TokenGuardian.Message.CantRenewToken, tokenHolderProbe.ref)
      watcher.expectTerminated(tokenHolderProbe.ref)

      awaitAssert({
        authenticateUser should ===(2)
      }, 300 millis)
    }

    "Route messages to TokenHolder when working normally" in {
      val accessToken = AccessTokenGen.sample()

      val tokenHolderProbe = TestProbe()
      implicit val userInfoInitalizer =
        TestIO.userInfoInitializerWithActor(tokenHolderProbe.ref)
      var authenticateUser = 0

      implicit val authenticationHandler =
        TestIO.authenticationHandlerWithValues(reAuthenticateParam = () => {
          authenticateUser += 1
          Either.right(Unit)
        })

      implicit val tokenHolderInitializer = new TokenHolderInitializer[TestIO] {
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

      val guardian = system.actorOf(TokenGuardian.props[TestIO](Seq.empty))
      guardian ! ApplicationStarted

      guardian ! Authenticated(accessToken)

      val customer = TestProbe()

      guardian.tell(TokenHolder.AskAccessToken, customer.ref)
      tokenHolderProbe.expectMsg(TokenHolder.AskAccessToken)
      tokenHolderProbe.lastSender should ===(customer.ref)
    }

    "Route tokenExpired to TokenHolder when working normally" in {
      val accessToken = AccessTokenGen.sample()

      val tokenHolderProbe = TestProbe()
      implicit val userInfoInitalizer =
        TestIO.userInfoInitializerWithActor(tokenHolderProbe.ref)
      var authenticateUser = 0

      implicit val authenticationHandler =
        TestIO.authenticationHandlerWithValues(reAuthenticateParam = () => {
          authenticateUser += 1
          Either.right(Unit)
        })

      implicit val tokenHolderInitializer = new TokenHolderInitializer[TestIO] {
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

      val guardian = system.actorOf(TokenGuardian.props[TestIO](Seq.empty))
      guardian ! ApplicationStarted

      guardian ! Authenticated(accessToken)

      val customer = TestProbe()

      guardian.tell(TokenHolder.TokenExpiredNeedNew, customer.ref)
      tokenHolderProbe.expectMsg(TokenHolder.TokenExpiredNeedNew)
      tokenHolderProbe.lastSender should ===(customer.ref)
    }

    "Create the UserInfo" in {
      val accessToken = AccessTokenGen.sample()

      val tokenHolderProbe = TestProbe()

      val userInfoProbe = TestProbe()

      implicit val userInfoInitalizer = new UserInfoInitializer[TestIO] {
        var numberUserInfoInitialized = 0

        override def initializeUserInfo(
          tokenGuardian: ActorRef,
          tokenHolder: ActorRef
        )(implicit system: ActorSystem,
          twitchClientHandler: TwitchClientHandler[TestIO],
          twitchClient: TwitchClient[TestIO],
          httpClient: HttpClient[TestIO],
          logProvider: LogProvider[TestIO],
          monad: Monad[TestIO]): ActorRef = {
          numberUserInfoInitialized += 1
          userInfoProbe.ref
        }
      }
      var authenticateUser = 0

      implicit val authenticationHandler =
        TestIO.authenticationHandlerWithValues(reAuthenticateParam = () => {
          authenticateUser += 1

          Either.right(Unit)
        })

      implicit val tokenHolderInitializer = new TokenHolderInitializer[TestIO] {
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

      val guardian = system.actorOf(
        TokenGuardian
          .props[TestIO](Seq(UserInfoInitializer.initializer[TestIO]))
      )
      guardian ! ApplicationStarted

      guardian ! Authenticated(accessToken)

      awaitAssert({
        tokenHolderInitializer.numberTokenHolderCreated should ===(1)
        userInfoInitalizer.numberUserInfoInitialized should ===(1)
        authenticateUser should ===(1)
      }, 200 millis)
    }

    "Kill the UserInfo when we need to re-authenticate" in {
      val accessToken = AccessTokenGen.sample()

      val tokenHolderProbe = TestProbe()

      val userInfoProbe = TestProbe()

      implicit val userInfoInitalizer = new UserInfoInitializer[TestIO] {
        var numberUserInfoInitialized = 0
        override def initializeUserInfo(
          tokenGuardian: ActorRef,
          tokenHolder: ActorRef
        )(implicit context: ActorSystem,
          twitchClientHandler: TwitchClientHandler[TestIO],
          twitchClient: TwitchClient[TestIO],
          httpClient: HttpClient[TestIO],
          logProvider: LogProvider[TestIO],
          monad: Monad[TestIO]): ActorRef = {
          numberUserInfoInitialized += 1
          userInfoProbe.ref
        }
      }
      var authenticateUser = 0

      implicit val authenticationHandler =
        TestIO.authenticationHandlerWithValues(reAuthenticateParam = () => {
          authenticateUser += 1

          Either.right(Unit)
        })

      val guardian = system.actorOf(
        TokenGuardian
          .props[TestIO](Seq(UserInfoInitializer.initializer[TestIO]))
      )
      guardian ! ApplicationStarted

      guardian ! Authenticated(accessToken)

      awaitAssert({
        userInfoInitalizer.numberUserInfoInitialized should ===(1)
        authenticateUser should ===(1)
      }, 200 millis)

      val watcher = TestProbe()

      watcher.watch(userInfoProbe.ref)

      guardian.tell(TokenGuardian.Message.CantRenewToken, tokenHolderProbe.ref)
      watcher.expectTerminated(userInfoProbe.ref)

      succeed
    }

    "Create service dependent on TokenHolder and provide it with service needed" in {
      val accessToken = AccessTokenGen.sample()
      val userInfo = TestProbe()

      import TokenGuardian.ServiceType._
      val userInfoInitializer = ServiceInitializer(UserInfo, (_, _) => userInfo.ref)

      val followers = TestProbe()

      import TokenGuardian.ServiceType._
      val followersInitializer = ServiceInitializer(Followers, (_, _) => followers.ref)

      var authenticateUser = 0

      implicit val authenticationHandler =
        TestIO.authenticationHandlerWithValues(reAuthenticateParam = () => {
          authenticateUser += 1

          Either.right(Unit)
        })

      val tokenHolderProbe = TestProbe()

      implicit val tokenHolderInitializer = new TokenHolderInitializer[TestIO] {
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

      val guardian = system.actorOf(TokenGuardian.props[TestIO](Seq(userInfoInitializer, followersInitializer)))
      guardian ! ApplicationStarted
      guardian ! Authenticated(accessToken)

      userInfo.expectMsg(CommonMessages.ApplicationStarted)
      userInfo.lastSender should === (guardian)
      followers.expectMsg(CommonMessages.ApplicationStarted)
      followers.lastSender should === (guardian)

      guardian.tell(RequireService(UserInfo), followers.ref)
      followers.expectMsg(ServiceProvide(UserInfo, userInfo.ref))
      followers.lastSender should === (guardian)
    }
  }
}

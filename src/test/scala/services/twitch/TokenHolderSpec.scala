package services.twitch

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import hazzlenut.services.twitch.{AccessToken, TokenHolder}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import cats.implicits._
import hazzlenut.services.twitch.TokenHolder.{AskAccessToken, ReplyAccessToken, TokenExpiredNeedNew}

import scala.concurrent.duration._
import scala.concurrent.Future

class TokenHolderSpec
    extends TestKit(ActorSystem("TokenHolderSpec"))
    with WordSpecLike
    with ImplicitSender
    with Matchers
    with BeforeAndAfterAll {

  import utils.TestIO._

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "Token Holder" should {
    "Return a access Token when provided with one" in {
      val accessToken = AccessToken(
        accessToken = "Token",
        tokenType = "bearer",
        expiresIn = 3500,
        refreshToken = "refreshToken".some
      )

      val tokenGuardian = TestProbe()

      val tokenHolder = system.actorOf(TokenHolder.props(accessToken, tokenGuardian.ref))

      tokenHolder ! AskAccessToken
      val reply = expectMsgType[ReplyAccessToken]

      reply.accessToken should have(
        'accessToken ("Token"),
        'tokenType ("bearer"),
        'expiresIn (3500),
        'refreshToken ("refreshToken".some)
      )
    }

    "When token expires refresh and return it to the asker" in {
      val accessToken = AccessToken(
        accessToken = "Token",
        tokenType = "bearer",
        expiresIn = 3500,
        refreshToken = "refreshToken".some
      )

      val tokenGuardian = TestProbe()

      implicit val ec = system.dispatcher

      val tokenHolder = system.actorOf(
        TokenHolder.props(accessToken, tokenGuardian.ref)(
          authenticationHandlerWithValues(refreshTokenValue = Future {
            Thread.sleep(60);

            (Either.right(
              AccessToken(
                accessToken = "Token2",
                tokenType = "bearer2",
                expiresIn = 1500,
                refreshToken = "refreshToken2".some
              )
            ))
          })
        )
      )

      tokenHolder ! TokenExpiredNeedNew
      expectNoMessage(50 millis)

      awaitAssert {
        val accessTokenReply = expectMsgType[ReplyAccessToken]

        accessTokenReply.accessToken should have(
          'accessToken ("Token2"),
          'tokenType ("bearer2"),
          'expiresIn (1500),
          'refreshToken ("refreshToken2".some)
        )
      }
    }

    "When second actor asks for access token and when the refresh is happening" in {
      val accessToken = AccessToken(
        accessToken = "Token",
        tokenType = "bearer",
        expiresIn = 3500,
        refreshToken = "refreshToken".some
      )

      val tokenGuardian = TestProbe()

      implicit val ec = system.dispatcher

      val firstAsker = TestProbe()
      val secondAsker = TestProbe()

      val tokenHolder = system.actorOf(
        TokenHolder.props(accessToken, tokenGuardian.ref)(
          authenticationHandlerWithValues(refreshTokenValue = Future {
            Thread.sleep(50);

            (Either.right(
              AccessToken(
                accessToken = "Token2",
                tokenType = "bearer2",
                expiresIn = 1500,
                refreshToken = "refreshToken2".some
              )
            ))
          })
        )
      )

      tokenHolder.tell(TokenExpiredNeedNew, firstAsker.ref)
      firstAsker.expectNoMessage(20 millis)

      tokenHolder.tell(AskAccessToken, secondAsker.ref)
      secondAsker.expectNoMessage(20 millis)

      firstAsker.awaitAssert {
        val accessTokenReply = firstAsker.expectMsgType[ReplyAccessToken]

        accessTokenReply.accessToken should have(
          'accessToken ("Token2"),
          'tokenType ("bearer2"),
          'expiresIn (1500),
          'refreshToken ("refreshToken2".some)
        )
      }

      secondAsker.awaitAssert {
        val accessTokenReply = secondAsker.expectMsgType[ReplyAccessToken]

        accessTokenReply.accessToken should have(
          'accessToken ("Token2"),
          'tokenType ("bearer2"),
          'expiresIn (1500),
          'refreshToken ("refreshToken2".some)
        )
      }
    }

    "When second actor tells that token is expired and the the refresh is happening" in {
      val accessToken = AccessToken(
        accessToken = "Token",
        tokenType = "bearer",
        expiresIn = 3500,
        refreshToken = "refreshToken".some
      )

      val tokenGuardian = TestProbe()

      implicit val ec = system.dispatcher

      val firstAsker = TestProbe()
      val secondAsker = TestProbe()

      val tokenHolder = system.actorOf(
        TokenHolder.props(accessToken, tokenGuardian.ref)(
          authenticationHandlerWithValues(refreshTokenValue = Future {
            Thread.sleep(50);

            (Either.right(
              AccessToken(
                accessToken = "Token2",
                tokenType = "bearer2",
                expiresIn = 1500,
                refreshToken = "refreshToken2".some
              )
            ))
          })
        )
      )

      tokenHolder.tell(TokenExpiredNeedNew, firstAsker.ref)
      firstAsker.expectNoMessage(20 millis)

      tokenHolder.tell(TokenExpiredNeedNew, secondAsker.ref)
      secondAsker.expectNoMessage(20 millis)

      firstAsker.awaitAssert {
        val accessTokenReply = firstAsker.expectMsgType[ReplyAccessToken]

        accessTokenReply.accessToken should have(
          'accessToken ("Token2"),
          'tokenType ("bearer2"),
          'expiresIn (1500),
          'refreshToken ("refreshToken2".some)
        )
      }

      secondAsker.awaitAssert {
        val accessTokenReply = secondAsker.expectMsgType[ReplyAccessToken]

        accessTokenReply.accessToken should have(
          'accessToken ("Token2"),
          'tokenType ("bearer2"),
          'expiresIn (1500),
          'refreshToken ("refreshToken2".some)
        )
      }
    }
  }
}

package services.twitch

import akka.actor.{ActorContext, ActorRef, ActorSystem}
import akka.testkit.{TestKit, TestProbe}
import hazzlenut.handler.AuthenticationHandler
import hazzlenut.services.twitch.{
  AccessToken,
  TokenGuardian,
  TokenHolderInitializer
}
import org.scalatest.{Matchers, WordSpecLike}
import utils.TestIO
import cats.implicits._
import hazzlenut.services.twitch.TokenGuardian.Authenticated

class TokenGuardianSpec
    extends TestKit(ActorSystem("TokenGuardianSpec"))
    with WordSpecLike
    with Matchers {

  "Token Guardian" should {
    "create token holder when it starts" in {
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

      // TODO guarantee that if need we give it time to execute
      //  cause this might yield to another thread (Not sync code)

      tokenHolderInitializer.numberTokenHolderCreated should ===(1)
      authenticateUser should ===(1)
    }
  }

}

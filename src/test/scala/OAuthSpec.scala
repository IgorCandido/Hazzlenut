import cats.Id
import hazzlenut.errors.HazzlenutError
import hazzlenut.twitch.{
  AccessToken,
  Authenticate,
  OAuth,
  OAuthImplicit,
  TwitchAppCredentials
}
import org.scalatest.FeatureSpec
import scalaz.zio.{DefaultRuntime, ZIO}
import cats.implicits._
import scalaz.zio.interop.catz._

object OAuthSpec {
  implicit def dummyOAuth: OAuth[Id] = new OAuth[Id] {
    override def obtainAccessToken: Id[AccessToken] = AccessToken("authed")
  }
}

class OAuthSpec extends FeatureSpec {
  val runtime = new DefaultRuntime {}

  Feature("OAuth Interface") {
    Scenario("Simple functional test") {
      import OAuthSpec._
      val result = Authenticate.run[Id]

      assert(result == "authed")
    }
  }

  Feature("OAuth with real Request") {
    Scenario("Auth get") {
      import OAuthImplicit._
      val authProgram =
        Authenticate.run[ZIO[TwitchAppCredentials, HazzlenutError, ?]]

      val result = runtime.unsafeRun(
        authProgram.provide(TwitchAppCredentials("testId", "testSecret"))
      )

      assert(result == "token")
    }
  }
}

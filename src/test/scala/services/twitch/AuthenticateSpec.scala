package services.twitch

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import cats.implicits._
import hazzlenut.services.twitch.{AccessToken, Authenticate}
import org.scalatest.{Matchers, WordSpec}
import scalaz.zio.DefaultRuntime
import utils.TestIO

import scala.concurrent.ExecutionContext

class AuthenticateSpec extends WordSpec with Matchers {
  implicit val system: ActorSystem = ActorSystem()
  implicit val ec: ExecutionContext = system.dispatcher
  implicit val mat: Materializer = ActorMaterializer()

  val runtime = new DefaultRuntime {}

  "Authenticate" should {
    "Succeed when OAuth url is asked" in {
      import TestIO.TestIOMonad
      implicit val oauth = TestIO.oAuthTestIOWithValues(
        Either.right(AccessToken("token")),
        Either.right("https://www.twitch.tv/".some)
      )
      val urlTestIO = Authenticate.getUrlToAuthenticate[TestIO]

      urlTestIO.result.fold(
        _ => fail("Shouldn't have failed"),
        result =>
          result.fold(fail("Url should have been retrieved")) {
            _ should ===("https://www.twitch.tv/")
        }
      )
    }

    "Succeed when OAuth token is asked" in {
      import TestIO.TestIOMonad
      implicit val oauth = TestIO.oAuthTestIOWithValues(
        Either.right(AccessToken("token")),
        Either.right("https://www.twitch.tv/".some)
      )
      val urlTestIO = Authenticate.authenticate[TestIO].run("code")

      urlTestIO.result
        .fold(_ => fail("Shouldn't have failed"), _ should ===("token"))
    }
  }
}

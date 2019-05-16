import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import hazzlenut.errors.HazzlenutError
import hazzlenut.services.twitch.{Authenticate, TwitchAppCredentials}
import org.scalatest.{Matchers, WordSpec}
import scalaz.zio.interop.catz._
import scalaz.zio.{DefaultRuntime, ZIO}
import utils.TestIO

import scala.concurrent.ExecutionContext

class OAuthSpec extends WordSpec with Matchers {
  implicit val system: ActorSystem = ActorSystem()
  implicit val ec: ExecutionContext = system.dispatcher
  implicit val mat: Materializer = ActorMaterializer()

  val runtime = new DefaultRuntime {}

  "OAuth Interface" should {
    "Simple functional test" in {
      import TestIO._
      val reader = Authenticate.authenticate[TestIO]
      val result = reader.run("test")

      assert(result.a == "authed")
    }
  }

  "OAuth with real Request" should {
    "Auth get" in {
      import hazzlenut.twitch.TwitchZIO._
      val authProgram =
        Authenticate.authenticate[ZIO[Any, HazzlenutError, ?]]

      val result = runtime.unsafeRun(authProgram.run("test"))

      assert(result == "token")
    }

    "Obtain the Auth for client" in {
      import hazzlenut.twitch.TwitchZIO._
      val getUrl =
        Authenticate.getUrlToAuthenticate[ZIO[Any, HazzlenutError, ?]]

      val result = runtime.unsafeRun(getUrl)

      result.fold(fail("Cause we did not get an auth Url")) { _ =>
        true
      }
    }
  }
}

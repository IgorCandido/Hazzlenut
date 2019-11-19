package services.twitch

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import cats.implicits._
import hazzlenut.errors.HazzlenutError.ThrowableError
import hazzlenut.services.twitch.adapters.{AccessToken, Authenticate}
import org.scalatest.{Matchers, WordSpec}
import zio.DefaultRuntime
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
      implicit val oauth = TestIO.oAuthTestIOWithValues()
      val urlTestIO = Authenticate.getUrlToAuthenticate[TestIO]

      urlTestIO.result.fold(
        _ => fail("Shouldn't have failed"),
        result =>
          result.fold(fail("Url should have been retrieved")) {
            _ should ===("https://www.twitch.tv/")
        }
      )
    }

    "Fail when OAuth url returns an error" in {
      import TestIO.TestIOMonad
      implicit val oauth = TestIO.oAuthTestIOWithValues(
        getUrl = Either.left(ThrowableError(new Exception("Error")))
      )
      val urlTestIO = Authenticate.getUrlToAuthenticate[TestIO]

      urlTestIO.result.fold(
        error =>
          error match {
            case ThrowableError(exception: Exception) =>
              exception.getMessage() should ===("Error")
            case _ => fail("Wrong error")
        },
        _ => fail("Should have returned an error")
      )
    }

    "Succeed when OAuth token is asked" in {
      import TestIO.TestIOMonad
      implicit val oauth = TestIO.oAuthTestIOWithValues()
      val urlTestIO = Authenticate.authenticate[TestIO].run("code")

      urlTestIO.result
        .fold(_ => fail("Shouldn't have failed"), _ should have ('accessToken ("authed"),
                                                                            'tokenType (""),
                                                                            'expiresIn (200),
                                                                            'refreshToken ("242adas".some)))
    }

    "Fail when OAuth token is asked but an error is returned" in {
      import TestIO.TestIOMonad
      implicit val oauth = TestIO.oAuthTestIOWithValues(
        accessToken = Either.left(ThrowableError(new Exception("Error")))
      )
      val urlTestIO = Authenticate.authenticate[TestIO].run("code")

      urlTestIO.result.fold(
        error =>
          error match {
            case ThrowableError(exception: Exception) =>
              exception.getMessage() should ===("Error")
            case _ => fail("Wrong error")
          },
        _ => fail("Should have returned an error")
      )
    }
  }
}

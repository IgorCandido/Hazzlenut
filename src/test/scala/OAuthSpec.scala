import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import cats.Monad
import hazzlenut.errors.HazzlenutError
import hazzlenut.services.twitch.{AccessToken, Authenticate, OAuth, TwitchAppCredentials}
import org.scalatest.{Matchers, WordSpec}
import scalaz.zio.interop.catz._
import scalaz.zio.{DefaultRuntime, ZIO}

import scala.concurrent.ExecutionContext

object OAuthSpec {
  case class TestIO[A](a: A)

  implicit def TestIOMonad = new Monad[TestIO] {
    override def flatMap[A, B](fa: TestIO[A])(f: A => TestIO[B]): TestIO[B] =
      f(fa.a)

    @annotation.tailrec
    override def tailRecM[A, B](a: A)(f: A => TestIO[Either[A, B]]): TestIO[B] =
      f(a) match {
        case TestIO(Right(b)) => TestIO(b)
        case TestIO(Left(a))  => tailRecM(a)(f)
      }

    override def pure[A](x: A): TestIO[A] = TestIO(x)
  }

  implicit def dummyOAuth: OAuth[TestIO] = new OAuth[TestIO] {
    override def getAuthorizeUrl(credential: TwitchAppCredentials)(
      implicit system: ActorSystem,
      ec: ExecutionContext,
      mat: Materializer
    ): TestIO[Option[String]] = ???
    override def obtainAccessToken(credential: TwitchAppCredentials, code: String)(
      implicit system: ActorSystem,
      ec: ExecutionContext,
      mat: Materializer
    ): TestIO[AccessToken] =
      TestIO(AccessToken("authed"))
  }
}

class OAuthSpec extends WordSpec with Matchers {
  implicit val system: ActorSystem = ActorSystem()
  implicit val ec: ExecutionContext = system.dispatcher
  implicit val mat: Materializer = ActorMaterializer()

  val runtime = new DefaultRuntime {}

  "OAuth Interface" should {
    "Simple functional test" in {
      import OAuthSpec._
      val reader = Authenticate.authenticate[TestIO]
      val result = reader.run((TwitchAppCredentials("test", "test"),"test"))

      assert(result.a == "authed")
    }
  }

  "OAuth with real Request" should {
    "Auth get" in {
      import hazzlenut.twitch.OAuthZIO._
      val authProgram =
        Authenticate.authenticate[ZIO[Any, HazzlenutError, ?]]

      val result = runtime.unsafeRun(
        authProgram.run(
          (TwitchAppCredentials(
            "n9pbmipzozy0q81rn9g97ht6g61oeq",
            "tm7s99klifle5mi05av8ffupi40x8p"
          ), "test")
        )
      )

      assert(result == "token")
    }

    "Obtain the Auth for client" in {
      import hazzlenut.twitch.OAuthZIO._
      val getUrl =
        Authenticate.getUrlToAuthenticate[ZIO[Any, HazzlenutError, ?]]

      val result = runtime.unsafeRun(getUrl.run(TwitchAppCredentials(
        "n9pbmipzozy0q81rn9g97ht6g61oeq",
        "tm7s99klifle5mi05av8ffupi40x8p"
      )))

      result.fold(fail("Cause we did not get an auth Url")){_ => true}
    }
  }
}

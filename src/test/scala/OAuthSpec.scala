import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import cats.{Id, Monad}
import hazzlenut.errors.HazzlenutError
import hazzlenut.twitch.{AccessToken, Authenticate, OAuth, OAuthImplicit, TwitchAppCredentials}
import org.scalatest.FeatureSpec
import scalaz.zio.{DefaultRuntime, IO, UIO, ZIO}
import cats.implicits._
import scalaz.zio.interop.catz._

import scala.concurrent.ExecutionContext

object OAuthSpec {
  case class TestIO[A](a: A)

  implicit def TestIOMonad = new Monad[TestIO]{
    override def flatMap[A, B](fa: TestIO[A])(f: A => TestIO[B]): TestIO[B] = f(fa.a)

    @annotation.tailrec
    override def tailRecM[A, B](a: A)(f: A => TestIO[Either[A, B]]): TestIO[B] = f(a) match {
      case TestIO(Right(b)) => TestIO(b)
      case TestIO(Left(a)) => tailRecM(a)(f)
    }

    override def pure[A](x: A): TestIO[A] = TestIO(x)
  }

  implicit def dummyOAuth: OAuth[TestIO] = new OAuth[TestIO] {
    override def getAuthorizeUrl(credential: TwitchAppCredentials)(implicit system: ActorSystem, ec: ExecutionContext, mat: Materializer): Option[String] = ???
    override def obtainAccessToken(credential: TwitchAppCredentials)(implicit system: ActorSystem,
                                   ec: ExecutionContext,
                                   mat: Materializer): TestIO[AccessToken] =
      TestIO(AccessToken("authed"))
  }
}

class OAuthSpec extends FeatureSpec {
  implicit val system: ActorSystem = ActorSystem()
  implicit val ec: ExecutionContext = system.dispatcher
  implicit val mat: Materializer = ActorMaterializer()

  val runtime = new DefaultRuntime {}

  Feature("OAuth Interface") {
    Scenario("Simple functional test") {
      import OAuthSpec._
      val reader = Authenticate.run[TestIO]
      val result = reader.run(TwitchAppCredentials("test", "test"))

      assert(result.a == "authed")
    }
  }

  Feature("OAuth with real Request") {
    Scenario("Auth get") {
      import OAuthImplicit._
      val authProgram =
        Authenticate.run[ZIO[Any, HazzlenutError, ?]]

      val result = runtime.unsafeRun(authProgram.run(
        TwitchAppCredentials(
          "n9pbmipzozy0q81rn9g97ht6g61oeq",
          "tm7s99klifle5mi05av8ffupi40x8p"
        )
      ))

      assert(result == "token")
    }
  }
}

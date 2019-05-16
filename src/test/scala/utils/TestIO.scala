package utils
import akka.actor.ActorSystem
import akka.stream.Materializer
import cats.Monad
import hazzlenut.services.twitch.{
  AccessToken,
  Configuration,
  OAuth,
  TwitchAppCredentials
}

import scala.concurrent.ExecutionContext

case class TestIO[A](a: A)

object TestIO {
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

  implicit val oAuthTestIO: OAuth[TestIO] = new OAuth[TestIO] {
    override def getAuthorizeUrl(config: Configuration.Config)(
      implicit system: ActorSystem,
      ec: ExecutionContext,
      mat: Materializer
    ): TestIO[Option[String]] = ???

    override def obtainAccessToken(code: String, config: Configuration.Config)(
      implicit system: ActorSystem,
      ec: ExecutionContext,
      mat: Materializer
    ): TestIO[AccessToken] =
      TestIO(AccessToken("authed"))
  }

  implicit val configurationTestIO = new Configuration[TestIO] {
    override def get(): TestIO[Configuration.Config] =
      TestIO(
        Configuration.Config(
          clientId = "n9pbmipzozy0q81rn9g97ht6g61oeq",
          clientSecret = "tm7s99klifle5mi05av8ffupi40x8p",
          redirectUri = "http://localhost:8000/oauth/reply",
          tokenUrl = "/oauth2/token",
          authorizeUrl = "/oauth2/authorize",
          siteUrl = "https://id.twitch.tv:443",
          "channel:read:subscriptions"
        )
      )
  }
}

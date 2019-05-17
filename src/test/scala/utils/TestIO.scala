package utils
import akka.actor.ActorSystem
import akka.stream.Materializer
import cats.{Monad, MonadError}
import cats.data.NonEmptyList
import hazzlenut.errors.HazzlenutError
import hazzlenut.services.twitch.{
  AccessToken,
  Configuration,
  OAuth,
  TwitchAppCredentials
}
import hazzlenut.util.MapGetterValidation
import hazzlenut.util.MapGetterValidation.ConfigurationValidation
import cats.data.ValidatedNel
import cats.implicits._

import scala.concurrent.ExecutionContext

case class TestIO[A](a: Either[HazzlenutError, A])

object TestIO {
  implicit def TestIOMonad =
    new Monad[TestIO] with MonadError[TestIO, HazzlenutError] {
      override def flatMap[A, B](fa: TestIO[A])(f: A => TestIO[B]): TestIO[B] =
        fa.a.fold(error => TestIO(Either.left(error)), a => f(a))
      @annotation.tailrec
      override def tailRecM[A, B](
        a: A
      )(f: A => TestIO[Either[A, B]]): TestIO[B] =
        f(a) match {
          case TestIO(Right(Right(b))) => TestIO(Either.right(b))
          case TestIO(Right(Left(a)))  => tailRecM(a)(f)
          case TestIO(Left(error))     => TestIO(Either.left(error))
        }

      override def pure[A](x: A): TestIO[A] = TestIO(Either.right(x))

      override def raiseError[A](e: HazzlenutError): TestIO[A] =
        TestIO(Either.left(e))

      override def handleErrorWith[A](
        fa: TestIO[A]
      )(f: HazzlenutError => TestIO[A]): TestIO[A] = fa match {
        case TestIO(Left(error))       => f(error)
        case result @ TestIO(Right(_)) => result
      }
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
      TestIO(Either.right(AccessToken("authed")))
  }

  implicit val configurationTestIO = new Configuration[TestIO] {
    override def pureAsync(
      f: => ConfigurationValidation[Configuration.Config]
    ): TestIO[ConfigurationValidation[Configuration.Config]] =
      TestIO(Either.right(f))

    override def getConfig(): (ConfigurationValidation[String],
                               ConfigurationValidation[String],
                               ConfigurationValidation[String],
                               ConfigurationValidation[String],
                               ConfigurationValidation[String],
                               ConfigurationValidation[String],
                               ConfigurationValidation[String]) =
      (
        "n9pbmipzozy0q81rn9g97ht6g61oeq".validNel,
        "tm7s99klifle5mi05av8ffupi40x8p".validNel,
        "http://localhost:8000/oauth/reply".validNel,
        "/oauth2/token".validNel,
        "/oauth2/authorize".validNel,
        "https://id.twitch.tv:443".validNel,
        "channel:read:subscriptions".validNel
      )

  }
}

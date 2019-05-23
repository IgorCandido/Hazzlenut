package utils
import akka.actor.ActorSystem
import akka.stream.Materializer
import cats.implicits._
import cats.{Monad, MonadError}
import hazzlenut.errors.HazzlenutError
import hazzlenut.services.twitch.{AccessToken, Configuration, OAuth}
import hazzlenut.util.MapGetterValidation.ConfigurationValidation

import scala.concurrent.ExecutionContext

case class TestIO[A](result: Either[HazzlenutError, A])

object TestIO {
  implicit def TestIOMonad =
    new Monad[TestIO] with MonadError[TestIO, HazzlenutError] {
      override def flatMap[A, B](fa: TestIO[A])(f: A => TestIO[B]): TestIO[B] =
        fa.result.fold(error => TestIO(Either.left(error)), a => f(a))
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

  def oAuthTestIOWithValues(
    accessToken: => Either[HazzlenutError, AccessToken] = Either.right(
      AccessToken(
        accessToken = "authed",
        tokenType = "",
        expiresIn = 200,
        refreshToken = "242adas".some
      )
    ),
    getUrl: => Either[HazzlenutError, Option[String]] =
      Either.right("https://www.twitch.tv/".some)
  ) =
    new OAuth[TestIO] {
      override def getAuthorizeUrl(config: Configuration.Config)(
        implicit system: ActorSystem,
        ec: ExecutionContext,
        mat: Materializer
      ): TestIO[Option[String]] = TestIO(getUrl)

      override def obtainAccessToken(code: String,
                                     config: Configuration.Config)(
        implicit system: ActorSystem,
        ec: ExecutionContext,
        mat: Materializer
      ): TestIO[AccessToken] =
        TestIO(accessToken)

      override def refreshAccessToken(refreshToken: String,
                                      config: Configuration.Config)(
        implicit system: ActorSystem,
        ec: ExecutionContext,
        mat: Materializer
      ): TestIO[AccessToken] =
        TestIO(accessToken)
    }

  implicit val oAuthTestIO: OAuth[TestIO] = oAuthTestIOWithValues(
    Either.right(
      AccessToken(
        accessToken = "authed",
        tokenType = "",
        expiresIn = 200,
        refreshToken = "242adas".some
      )
    ),
    Either.right("url".some)
  )

  def configurationTestIOWithValues(
    config: (ConfigurationValidation[String],
             ConfigurationValidation[String],
             ConfigurationValidation[String],
             ConfigurationValidation[String],
             ConfigurationValidation[String],
             ConfigurationValidation[String],
             ConfigurationValidation[String])
  ) = new Configuration[TestIO] {
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
                               ConfigurationValidation[String]) = config
  }

  implicit val configurationTestIO = configurationTestIOWithValues(
    (
      "f2m0fm2m02m3vm2im205m2".validNel,
      "c2imc02mc02m0c2im0c2imc3232r".validNel,
      "http://localhost:8000/oauth/reply".validNel,
      "/oauth2/token".validNel,
      "/oauth2/authorize".validNel,
      "https://id.twitch.tv:443".validNel,
      "channel:read:subscriptions".validNel
    )
  )
}

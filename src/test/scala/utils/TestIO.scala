package utils
import akka.actor.{ActorContext, ActorRef, ActorSystem}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpRequest, HttpResponse, StatusCode, StatusCodes}
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.stream.Materializer
import cats.implicits._
import cats.{Monad, MonadError}
import hazzlenut.errors.HazzlenutError
import hazzlenut.errors.HazzlenutError.{ThrowableError, UnableToConnect, UnableToFetchUserInformation}
import hazzlenut.handler.{AuthenticationHandler, TwitchClientHandler}
import hazzlenut.services.twitch.model.User
import hazzlenut.services.twitch.{AccessToken, Configuration, OAuth, TwitchClient, UserInfo, UserInfoInitializer}
import hazzlenut.util.MapGetterValidation.ConfigurationValidation
import hazzlenut.util.{HttpClient, UnmarshallerEntiy}
import utils.TestIO.httpClientTestIO

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

case class TestIO[A](result: Either[HazzlenutError, A])

// Trying to correlate Out with S somehow maybe with implicit
trait TestGen[F[_], Out] {
  def to[S](implicit s: S =:= Out): F[S]
}

trait TestIOMonad {
  implicit val TestIOMonad =
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
}

trait TestIOAuth {
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

  implicit val authentactioHandler: AuthenticationHandler =
    authenticationHandlerWithValues(
      getAuthValue = Future.successful("authUrl".some),
      obtainOAuthValue = Future.successful(
        AccessToken(
          accessToken = "authed",
          tokenType = "",
          expiresIn = 200,
          refreshToken = "242adas".some
        )
      ),
      refreshTokenValue = Future.successful(
        Either.right(
          AccessToken(
            accessToken = "authed",
            tokenType = "",
            expiresIn = 200,
            refreshToken = "242adas".some
          )
        )
      )
    )

  def authenticationHandlerWithValues(
    getAuthValue: Future[Option[String]] = Future.successful("authUrl".some),
    obtainOAuthValue: Future[AccessToken] = Future.successful(
      AccessToken(
        accessToken = "authed",
        tokenType = "",
        expiresIn = 200,
        refreshToken = "242adas".some
      )
    ),
    refreshTokenValue: Future[Either[HazzlenutError, AccessToken]] =
      Future.successful(
        Either.right(
          AccessToken(
            accessToken = "authed",
            tokenType = "",
            expiresIn = 200,
            refreshToken = "242adas".some
          )
        )
      ),
    reAuthenticateParam: () => Either[HazzlenutError, Unit] = () =>
      Either.right(Unit)
  ): AuthenticationHandler =
    new AuthenticationHandler {
      override def getAuthUrl(implicit system: ActorSystem,
                              ec: ExecutionContext,
                              mat: Materializer): Future[Option[String]] =
        getAuthValue

      override def obtainOAuth(code: String)(
        implicit system: ActorSystem,
        ec: ExecutionContext,
        mat: Materializer
      ): Future[AccessToken] = obtainOAuthValue

      override def refreshToken(code: String)(
        implicit system: ActorSystem,
        ec: ExecutionContext,
        mat: Materializer
      ): Future[Either[HazzlenutError, AccessToken]] = refreshTokenValue

      override def reAuthenticate(): Either[HazzlenutError, Unit] =
        reAuthenticateParam()
    }

}

trait TestIOConfig {
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

trait TestIOHttpClient {
  implicit def httpClientTestIO(
    testIO: TestIO[HttpResponse]
  )(implicit actorSystem: ActorSystem) =
    new HttpClient[TestIO] {
      override def request(httpRequest: HttpRequest): TestIO[HttpResponse] = {
        testIO
      }

    }

  def httpClientWithCustomStatusCode(
    reply: String,
    statusCode: StatusCode
  ): HttpClient[TestIO] = {
    httpClient(
      implicitly[MonadError[TestIO, HazzlenutError]]
        .pure(
          HttpResponse(
            entity = HttpEntity(ContentTypes.`application/json`, reply),
            status = statusCode
          )
        )
    )
  }

  def httpClientSucess(defaultReply: String): HttpClient[TestIO] =
    httpClientWithCustomStatusCode(defaultReply, StatusCodes.OK)

  implicit def httpClient(testIO: TestIO[HttpResponse]) =
    new HttpClient[TestIO] {
      override def request(httpRequest: HttpRequest): TestIO[HttpResponse] =
        testIO

    }

  implicit val defaultEmptyResponseHttpClient: HttpClient[TestIO] =
    TestIO.httpClient(TestIO(Either.right(HttpResponse())))

}

trait TestIOUnmarshall {
  def unmarshallerEntiy[Out](testIO: TestIO[Out]): UnmarshallerEntiy[TestIO] =
    new UnmarshallerEntiy[TestIO] {
      override def unmarshalInternal[T, S](entity: T)(
        implicit materializer: Materializer,
        unmarshaller: Unmarshaller[T, S]
      ): TestIO[S] =
        testIO.asInstanceOf[TestIO[S]]
    }

  implicit val unmarshallerEntiy = new UnmarshallerEntiy[TestIO] {
    override def unmarshalInternal[T, S](entity: T)(
      implicit materializer: Materializer,
      unmarshaller: Unmarshaller[T, S]
    ): TestIO[S] = {
      TestIO {
        Unmarshal(entity)
          .to[S]
          .value
          .fold(Either.left[HazzlenutError, S](UnableToConnect)) { res =>
            res match {
              case Success(value) => Either.right[HazzlenutError, S](value)
              case Failure(throwable) =>
                Either.left[HazzlenutError, S](ThrowableError(throwable))
            }
          }
      }
    }
  }
}

trait TestIOTwitchClient {
  def createTwitchClient(
    userReturn: => TestIO[User] = TestIO(Either.right(UserGen.getSample())),
    followersReturn: TestIO[Seq[User]] = TestIO(
      Either.right(Seq(UserGen.getSample()))
    )
  ) =
    new TwitchClient[TestIO] {
      override def fromOption[Out](
        optionOf: Option[Out],
        hazzlenutError: HazzlenutError
      )(implicit monadError: MonadError[TestIO, HazzlenutError]): TestIO[Out] =
        ???

      override def user(accessToken: AccessToken)(
        implicit actorSystem: ActorSystem,
        materializer: Materializer,
        httpClient: HttpClient[TestIO],
        unmarshallerEntiy: UnmarshallerEntiy[TestIO],
        monadF: Monad[TestIO],
        monadError: MonadError[TestIO, HazzlenutError]
      ): TestIO[User] = userReturn

      override def followers(accessToken: AccessToken, userId: String)(
        implicit actorSystem: ActorSystem,
        materializer: Materializer,
        httpClient: HttpClient[TestIO],
        unmarshallerEntiy: UnmarshallerEntiy[TestIO],
        monadF: Monad[TestIO],
        monadError: MonadError[TestIO, HazzlenutError]
      ): TestIO[Seq[User]] = followersReturn
    }

  implicit val twitchClient = new TwitchClient[TestIO] {
    override def fromOption[Out](
      optionOf: Option[Out],
      hazzlenutError: HazzlenutError
    )(implicit monadError: MonadError[TestIO, HazzlenutError]): TestIO[Out] =
      optionOf match {
        case None         => monadError.raiseError(hazzlenutError)
        case Some(result) => monadError.pure[Out](result)
      }

    override def user(accessToken: AccessToken)(
      implicit actorSystem: ActorSystem,
      materializer: Materializer,
      httpClient: HttpClient[TestIO],
      unmarshallerEntiy: UnmarshallerEntiy[TestIO],
      monadF: Monad[TestIO],
      monadError: MonadError[TestIO, HazzlenutError]
    ): TestIO[User] =
      doRequest[User](
        "http://testUser",
        accessToken,
        UnableToFetchUserInformation
      )

    override def followers(accessToken: AccessToken, userId: String)(
      implicit actorSystem: ActorSystem,
      materializer: Materializer,
      httpClient: HttpClient[TestIO],
      unmarshallerEntiy: UnmarshallerEntiy[TestIO],
      monadF: Monad[TestIO],
      monadError: MonadError[TestIO, HazzlenutError]
    ): TestIO[Seq[User]] =
      doRequestSeq[User](
        "http://testUsers",
        accessToken,
        UnableToFetchUserInformation
      )
  }

  implicit val twitchHandler =
    new TwitchClientHandler[TestIO] {
      override def retrieveUser(accessToken: AccessToken)(
        implicit twitchClient: TwitchClient[TestIO],
        httpClient: HttpClient[TestIO],
        actorSystem: ActorSystem,
        materializer: Materializer
      ): Future[User] = {
        twitchClient
          .user(accessToken)
          .result
          .fold(error => Future.failed(error), user => Future.successful(user))
      }
    }
}

trait TestIOUserInfoInitializer {
  type UserInfoInitializerType = (ActorContext,
                                  TwitchClientHandler[TestIO],
                                  TwitchClient[TestIO],
                                  HttpClient[TestIO]) => ActorRef

  implicit def dummyActorRef(actorRef: ActorRef) =
    (_: ActorContext,
     _: TwitchClientHandler[TestIO],
     _: TwitchClient[TestIO],
     _: HttpClient[TestIO]) => actorRef

  def userInfoInitializer(tokenHolder: ActorRef) =
    userInfoInitializerWithActor(
      (context: ActorContext,
       _: TwitchClientHandler[TestIO],
       _: TwitchClient[TestIO],
       _: HttpClient[TestIO]) =>
        context.actorOf(UserInfo.props[TestIO](tokenHolder))
    )

  def userInfoInitializerWithActor(actorRefGenerator: UserInfoInitializerType) =
    new UserInfoInitializer[TestIO] {
      override def initializeUserInfo(tokenHolder: ActorRef)(
        implicit context: ActorContext,
        twitchClientHandler: TwitchClientHandler[TestIO],
        twitchClient: TwitchClient[TestIO],
        httpClient: HttpClient[TestIO]
      ): ActorRef =
        actorRefGenerator(
          context,
          twitchClientHandler,
          twitchClient,
          httpClient
        )
    }
}

object TestIO
    extends TestIOMonad
    with TestIOAuth
    with TestIOConfig
    with TestIOHttpClient
    with TestIOUnmarshall
    with TestIOTwitchClient
    with TestIOUserInfoInitializer {}

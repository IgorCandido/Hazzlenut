package hazzlenut.services.twitch.adapters

import cats.Monad
import cats.implicits._
import hazzlenut.errors.HazzlenutError
import hazzlenut.errors.HazzlenutError.{InvalidConfiguration, MonadErrorHazzlenut}
import hazzlenut.services.twitch.adapters.Configuration.Config
import hazzlenut.util.MapGetterValidation.{ConfigurationValidation, _}
import zio.ZIO

trait Configuration[F[_]] {
  import hazzlenut.util.MapGetterValidation._
  def get(implicit m: Monad[F], mError: MonadErrorHazzlenut[F]): F[Config] =
    for {
      configOrValidationError <- pureAsync {
        (
          getConfig()
        ).mapN(Configuration.Config.apply)
      }
      validConfig <- configOrValidationError.fold(
        errors =>
          mError.raiseError(
            InvalidConfiguration(
              errors
                .map(e => s"${e.name}: ${e.error}")
                .mkString_("", ",", "")
            )
        ),
        config => mError.pure(config)
      )
    } yield validConfig

  def pureAsync(
    f: => ConfigurationValidation[Config]
  ): F[ConfigurationValidation[Config]]

  def getConfig(): (ConfigurationValidation[String],
                    ConfigurationValidation[String],
                    ConfigurationValidation[String],
                    ConfigurationValidation[String],
                    ConfigurationValidation[String],
                    ConfigurationValidation[String],
                    ConfigurationValidation[String])
}

object Configuration {

  def apply[F[_]](implicit configuration: Configuration[F]): Configuration[F] =
    configuration

  case class Config(clientId: String,
                    clientSecret: String,
                    redirectUri: String,
                    tokenUrl: String,
                    authorizeUrl: String,
                    siteUrl: String,
                    scopes: String*)

  object Config {
    def apply(clientId: String,
              clientSecret: String,
              redirectUri: String,
              tokenUrl: String,
              authorizeUrl: String,
              siteUrl: String,
              scopes: String) =
      new Config(
        clientId = clientId,
        clientSecret = clientSecret,
        redirectUri = redirectUri,
        tokenUrl = tokenUrl,
        authorizeUrl = authorizeUrl,
        siteUrl = siteUrl,
        scopes.split(','): _*
      )
  }

  object dsl {
    def get[F[_]: Monad: MonadErrorHazzlenut](
      implicit configuration: Configuration[F]
    ): F[Configuration.Config] =
      configuration.get
  }

  implicit val configurationZIO =
    new Configuration[ZIO[Any, HazzlenutError, ?]] {

      override def pureAsync(
        f: => ConfigurationValidation[Configuration.Config]
      ): ZIO[Any, HazzlenutError, ConfigurationValidation[
        Configuration.Config
      ]] = ZIO.succeedLazy(f)

      override def getConfig(): (ConfigurationValidation[String],
                                 ConfigurationValidation[String],
                                 ConfigurationValidation[String],
                                 ConfigurationValidation[String],
                                 ConfigurationValidation[String],
                                 ConfigurationValidation[String],
                                 ConfigurationValidation[String]) =
        (
          sys.env.getMandatory("clientId"),
          sys.env.getMandatory("clientSecret"),
          sys.env.getMandatory("redirectUri"),
          sys.env.getMandatory("tokenUrl"),
          sys.env.getMandatory("authorizeUrl"),
          sys.env.getMandatory("siteUrl"),
          sys.env.getMandatory("scopes")
        )
    }
}

package hazzlenut.services.twitch

import cats.implicits._
import cats.{Monad, MonadError}
import hazzlenut.errors.{HazzlenutError, InvalidConfiguration}
import hazzlenut.services.twitch.Configuration.Config

trait Configuration[F[_]] {
  import hazzlenut.util.MapGetterValidation._
  def get(implicit m: Monad[F],
          mError: MonadError[F, HazzlenutError]): F[Config] =
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
        scopes.split('c'): _*
      )
  }

  case class Config(clientId: String,
                    clientSecret: String,
                    redirectUri: String,
                    tokenUrl: String,
                    authorizeUrl: String,
                    siteUrl: String,
                    scopes: String*)

  def apply[F[_]](implicit configuration: Configuration[F]): Configuration[F] =
    configuration

  object dsl {
    def get[F[_]: Monad](
      implicit configuration: Configuration[F],
      mError: MonadError[F, HazzlenutError]
    ): F[Configuration.Config] =
      configuration.get
  }
}

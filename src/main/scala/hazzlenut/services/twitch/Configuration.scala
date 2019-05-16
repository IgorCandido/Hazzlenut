package hazzlenut.services.twitch

import cats.Monad
import cats.data.NonEmptyList
import hazzlenut.errors.{HazzlenutError, InvalidConfiguration}
import hazzlenut.services.twitch.Configuration.Config
import cats.implicits._
import cats.instances.all._

trait Configuration[F[_]] {
  import hazzlenut.util.MapGetterValidation._
  def getProgram(implicit m: Monad[F]): F[Config] =
    for {
      configOrValidationError <- pureAsync {
        (
          sys.env.getMandatory("clientId"),
          sys.env.getMandatory("clientSecret"),
          sys.env.getMandatory("redirectUri"),
          sys.env.getMandatory("tokenUrl"),
          sys.env.getMandatory("authorizeUrl"),
          sys.env.getMandatory("siteUrl"),
          sys.env.getMandatory("scopes")
        ).mapN(Configuration.Config.apply)
      }
      validConfig <- wrapError(configOrValidationError){errors =>
        InvalidConfiguration(
            errors
              .map(e => s"${e.name}: ${e.error}")
              .mkString_("", ",", "")
          )
        }
    } yield validConfig

  def pureAsync(f: => ConfigurationValidation[Config]): F[ConfigurationValidation[Config]]

  def wrapError(f: => ConfigurationValidation[Config])(fail: NonEmptyList[FieldError] => HazzlenutError): F[Config]

  def get(): F[Config]
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
    def get[F[_]](
      implicit configuration: Configuration[F]
    ): F[Configuration.Config] =
      configuration.get
  }
}

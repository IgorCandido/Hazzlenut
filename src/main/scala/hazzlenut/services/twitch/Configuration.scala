package hazzlenut.services.twitch

import hazzlenut.services.twitch.Configuration.Config

trait Configuration[F[_]] {
  def get(): F[Config]
}

object Configuration {
  case class Config(redirectUri: String,
                    tokenUrl: String,
                    authorizeUrl: String,
                    siteUrl: String,
                    scopes: String*)

  def apply[F[_]](implicit configuration: Configuration[F]): Configuration[F] =
    configuration

  object dsl {
    def get[F[_]](implicit configuration: Configuration[F]): F[Configuration.Config] =
      configuration.get
  }
}

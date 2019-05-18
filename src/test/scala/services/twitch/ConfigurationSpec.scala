package services.twitch

import cats.implicits._
import hazzlenut.errors.HazzlenutError.InvalidConfiguration
import hazzlenut.util.MapGetterValidation.FieldError
import org.scalatest.{Matchers, WordSpec}
import utils.TestIO

class ConfigurationSpec extends WordSpec with Matchers {

  "Configuration" should {
    "Read successfully when all properties are provided" in {
      import TestIO.TestIOMonad
      import hazzlenut.services.twitch.Configuration.dsl._
      implicit val configurationProvider = TestIO.configurationTestIOWithValues(
        (
          "sad2532y54hh5uyuu5jh5j5".validNel,
          "3vsfm3in3nf3nf3n3fnn3f93f3".validNel,
          "http://localhost:8000/oauth/reply".validNel,
          "/oauth2/token".validNel,
          "/oauth2/authorize".validNel,
          "https://id.twitch.tv:443".validNel,
          "channel:read:subscriptions".validNel
        )
      )

      val configuration = get[TestIO]

      configuration.result
        .fold(
          _ => fail("Shouldn't have returned an error"),
          result => {
            result should have(
              'clientId ("sad2532y54hh5uyuu5jh5j5"),
              'clientSecret ("3vsfm3in3nf3nf3n3fnn3f93f3"),
              'redirectUri ("http://localhost:8000/oauth/reply"),
              'tokenUrl ("/oauth2/token"),
              'authorizeUrl ("/oauth2/authorize"),
              'siteUrl ("https://id.twitch.tv:443")
            )
            result.scopes should contain("channel:read:subscriptions")
          }
        )
    }

    "Read successfully when all properties are provided and multiple scopes" in {
      import TestIO.TestIOMonad
      import hazzlenut.services.twitch.Configuration.dsl._
      implicit val configurationProvider = TestIO.configurationTestIOWithValues(
        (
          "sad2532y54hh5uyuu5jh5j5".validNel,
          "3vsfm3in3nf3nf3n3fnn3f93f3".validNel,
          "http://localhost:8000/oauth/reply".validNel,
          "/oauth2/token".validNel,
          "/oauth2/authorize".validNel,
          "https://id.twitch.tv:443".validNel,
          "channel:read:subscriptions,channel:write:subscriptions".validNel
        )
      )

      val configuration = get[TestIO]

      configuration.result
        .fold(
          _ => fail("Shouldn't have returned an error"),
          result => {
            result should have(
              'clientId ("sad2532y54hh5uyuu5jh5j5"),
              'clientSecret ("3vsfm3in3nf3nf3n3fnn3f93f3"),
              'redirectUri ("http://localhost:8000/oauth/reply"),
              'tokenUrl ("/oauth2/token"),
              'authorizeUrl ("/oauth2/authorize"),
              'siteUrl ("https://id.twitch.tv:443")
            )
            result.scopes should (contain("channel:read:subscriptions") and contain(
              "channel:write:subscriptions"
            ))
          }
        )
    }

    "Read with one error when the property scopes is not available" in {
      import TestIO.TestIOMonad
      import hazzlenut.services.twitch.Configuration.dsl._
      implicit val configurationProvider = TestIO.configurationTestIOWithValues(
        (
          "sad2532y54hh5uyuu5jh5j5".validNel,
          "3vsfm3in3nf3nf3n3fnn3f93f3".validNel,
          "http://localhost:8000/oauth/reply".validNel,
          "/oauth2/token".validNel,
          "/oauth2/authorize".validNel,
          "https://id.twitch.tv:443".validNel,
          FieldError("scopes", "Not found").invalidNel
        )
      )

      val configuration = get[TestIO]

      configuration.result
        .fold(
          error =>
            error match {
              case InvalidConfiguration(fieldError) =>
                fieldError should ===(s"scopes: Not found")
              case _ => fail("Wrong error returned")
          },
          _ => fail("Should have returned an error")
        )
    }

    "Read with one error when the properties scopes and siteUrl are not available" in {
      import TestIO.TestIOMonad
      import hazzlenut.services.twitch.Configuration.dsl._
      implicit val configurationProvider = TestIO.configurationTestIOWithValues(
        (
          "sad2532y54hh5uyuu5jh5j5".validNel,
          "3vsfm3in3nf3nf3n3fnn3f93f3".validNel,
          "http://localhost:8000/oauth/reply".validNel,
          "/oauth2/token".validNel,
          "/oauth2/authorize".validNel,
          FieldError("siteUrl", "Not found").invalidNel,
          FieldError("scopes", "Not found").invalidNel
        )
      )

      val configuration = get[TestIO]

      configuration.result
        .fold(
          error =>
            error match {
              case InvalidConfiguration(fieldError) =>
                fieldError should ===(s"siteUrl: Not found,scopes: Not found")
              case _ => fail("Wrong error returned")
          },
          _ => fail("Should have returned an error")
        )
    }
  }
}

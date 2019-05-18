package hazzlenut.util

import cats.data.ValidatedNel
import hazzlenut.errors.HazzlenutError
import cats.implicits._

object MapGetterValidation {
  type ConfigurationValidation[A] = ValidatedNel[FieldError, A]

  case class FieldError(name: String, error: String)

  implicit class MapOps[V](val map: Map[String, V]) extends AnyVal {
    def getMandatory(name: String): ConfigurationValidation[V] =
      map
        .get(name)
        .fold(FieldError(name, "Couldn't find").invalidNel[V])(
          _.validNel[FieldError]
        )
  }
}

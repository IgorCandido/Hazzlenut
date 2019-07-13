package hazzlenut.errors

import cats.MonadError

sealed trait HazzlenutError extends Throwable

object HazzlenutError{
  type MonadErrorHazzlenut[F[_]] = MonadError[F, HazzlenutError]

  final case class ThrowableError(throwable: Throwable) extends HazzlenutError

  final case class InvalidConfiguration(fieldErrors: String) extends HazzlenutError

  final case object UnableToFetchUserInformation extends HazzlenutError

  final case object UnableToFetchFollowers extends HazzlenutError

  final case object UnableToConnect extends HazzlenutError

  final case object UnableToAuthenticate extends HazzlenutError

  final case class UnmarshallError(throwable: Throwable) extends HazzlenutError
}



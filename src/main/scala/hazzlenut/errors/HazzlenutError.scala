package hazzlenut.errors

trait HazzlenutError extends Throwable

case class ThrowableError(throwable: Throwable) extends HazzlenutError

case class InvalidConfiguration(fieldErrors: String) extends HazzlenutError

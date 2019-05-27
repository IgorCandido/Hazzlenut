package hazzlenut.services.twitch

import hazzlenut.errors.HazzlenutError

trait Reauthentication[F[_]] {
  def requestAuthenticationFromUser(): F[Either[HazzlenutError,Unit]]
}

object Reauthentication{
  def apply[F[_]](implicit F: Reauthentication[F]): Reauthentication[F] = F

  object dsl {
    def reauthenticateWithUser[F[_]: Reauthentication](): F[Either[HazzlenutError, Unit]] =
      Reauthentication[F].requestAuthenticationFromUser()
  }
}

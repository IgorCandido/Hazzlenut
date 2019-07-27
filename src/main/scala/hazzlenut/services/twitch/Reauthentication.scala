package hazzlenut.services.twitch

import hazzlenut.errors.HazzlenutError
import hazzlenut.errors.HazzlenutError.ThrowableError
import zio.ZIO

import scala.util.Try

trait Reauthentication[F[_]] {
  def requestAuthenticationFromUser(): F[Either[HazzlenutError,Unit]]
}

object Reauthentication{
  def apply[F[_]](implicit F: Reauthentication[F]): Reauthentication[F] = F

  object dsl {
    def reauthenticateWithUser[F[_]: Reauthentication](): F[Either[HazzlenutError, Unit]] =
      Reauthentication[F].requestAuthenticationFromUser()
  }

  implicit val ReauthenticateZIO: Reauthentication[ZIO[Any, HazzlenutError, ?]] = new Reauthentication[ZIO[Any, HazzlenutError, ?]] {
    override def requestAuthenticationFromUser()
    : ZIO[Any, HazzlenutError, Either[HazzlenutError, Unit]] = {
      import java.awt.Desktop
      import java.net.URI

      ZIO.fromTry {
        Try {
          if (Desktop.isDesktopSupported() && Desktop
            .getDesktop()
            .isSupported(Desktop.Action.BROWSE)) {
            Desktop
              .getDesktop()
              .browse(new URI("http://localhost:8000/oauth/login"))
          }
        }
      }.mapError{throwable => ThrowableError(throwable)}.either
    }
  }
}

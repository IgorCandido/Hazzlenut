package hazzlenut.util


import cats.instances.all._
import cats.syntax.all._
import cats.{Contravariant, MonadError}
import hazzlenut.errors.HazzlenutError
import hazzlenut.errors.HazzlenutError.ThrowableError
import scalaz.zio.ZIO

trait WidenMonadError[F[_], E, E2] {
  def widen(implicit monadError: MonadError[F, E], ev: E <:< E2): MonadError[F, E2]
}

//TODO finish implementing the widen from MonadError of HazzlenutError to MonadError of Throwable
object WidenMonadError {
  implicit def widenHazzlenutErrorToThrowable = new WidenMonadError[ZIO[Any, HazzlenutError, ?], HazzlenutError, Throwable] {
    override def widen(implicit monadError: MonadError[ZIO[Any, HazzlenutError, ?], HazzlenutError], ev: HazzlenutError <:< Throwable): MonadError[ZIO[Any, HazzlenutError, ?], Throwable] = new MonadError[ZIO[Any, HazzlenutError, ?], Throwable] {


      override def raiseError[A](e: Throwable): ZIO[Any, HazzlenutError, A] = monadError.raiseError(ThrowableError(e))

      override def handleErrorWith[A](fa: ZIO[Any, HazzlenutError, A])
                                     (f: Throwable => ZIO[Any, HazzlenutError, A])
                                      : ZIO[Any, HazzlenutError, A] = {
        monadError.handleErrorWith(fa)(f)
      }

      override def pure[A](x: A): ZIO[Any, HazzlenutError, A] = monadError.pure(x)

      override def flatMap[A, B](fa: ZIO[Any, HazzlenutError, A])(f: A => ZIO[Any, HazzlenutError, B]): ZIO[Any, HazzlenutError, B] = monadError.flatMap(fa)(f)

      override def tailRecM[A, B](a: A)(f: A => ZIO[Any, HazzlenutError, Either[A, B]]): ZIO[Any, HazzlenutError, B] = monadError.tailRecM(a)(f)
    }

  }
}

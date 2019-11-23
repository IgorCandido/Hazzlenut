package hazzlenut.services.twitch.actor.helper

import hazzlenut.HazzleNutZIO
import zio.DefaultRuntime

trait Executor[F[_]] {
  def unsafeRun[A](f: F[A]): A

  def toEither[A](f: F[A]): Either[Throwable, A]
}

object Executor{
  object dsl {
    implicit class ExecutorOps[A, F[_]](val f: F[A]) extends AnyVal{
      def unsafeRun(implicit executor: Executor[F]): A =
        executor.unsafeRun[A](f)

      def toEither(implicit executor: Executor[F]): Either[Throwable, A] =
        executor.toEither[A](f)
    }

    def unsafeRun[A, F[_]](f: F[A])(implicit executor: Executor[F]): A =
      executor.unsafeRun(f)

    def toEither[A, F[_]](f: F[A])(implicit executor: Executor[F]): Either[Throwable, A] =
      executor.toEither(f)
  }

  implicit def executorHazzleNutZio(implicit runTime: DefaultRuntime) = new Executor[HazzleNutZIO] {
    override def unsafeRun[A](f: HazzleNutZIO[A]): A =
      runTime.unsafeRun(f)

    override def toEither[A](f: HazzleNutZIO[A]): Either[Throwable, A] =
      runTime.unsafeRun(f.either)
  }
}

package hazzlenut.util

import _root_.zio.{IO, Task, UIO, ZIO}
import cats.Monad
import hazzlenut.errors.HazzlenutError
import hazzlenut.errors.HazzlenutError.ThrowableError
import log.effect.internal.EffectSuspension
import log.effect.{LogLevel, LogWriter, LogWriterConstructor, internal}
import org.{log4s => l4s}
import cats.implicits._

trait LogProvider[F[_]] {
  def getLoggerByName(name: String): F[LogWriter[F]]
}

object LogProvider {
  import instances._

  def log[F[_]: Monad](name: String, logLevel: LogLevel, msg: => String)(implicit logProvider: LogProvider[F]): F[Unit] =
    (for{
      logger <- logProvider.getLoggerByName(name)
      _ <- logger.write(logLevel, msg)
    }yield ())


  object instances {
    implicit final val taskEffectSuspension
      : EffectSuspension[ZIO[Any, HazzlenutError, ?]] =
      new EffectSuspension[ZIO[Any, HazzlenutError, ?]] {
        def suspend[A](a: => A): ZIO[Any, HazzlenutError, A] =
          IO.effect(a).mapError(error => ThrowableError(error))
      }

    implicit final val uioEffectSuspension: EffectSuspension[UIO] =
      new EffectSuspension[UIO] {
        def suspend[A](a: => A): UIO[A] = IO.effectTotal(a)
      }

    implicit final def functorInstances[R, E]: internal.Functor[ZIO[R, E, ?]] =
      new internal.Functor[ZIO[R, E, ?]] {
        def fmap[A, B](f: A => B): ZIO[R, E, A] => ZIO[R, E, B] = _ map f
      }
  }

  val log4sFromName1
    : ZIO[String, Throwable, LogWriter[ZIO[Any, HazzlenutError, ?]]] =
    ZIO.accessM { name =>
      LogWriter
        .from[Task]
        .runningEffect[ZIO[Any, HazzlenutError, ?]](
          ZIO.effect(l4s.getLogger(name))
        )(
          LogWriterConstructor
            .log4sConstructor[Task, ZIO[Any, HazzlenutError, ?]](
              functorInstances,
              taskEffectSuspension
            )
        )
    }

  implicit object ZIOLogProvider
      extends LogProvider[ZIO[Any, HazzlenutError, ?]] {
    override def getLoggerByName(
      name: String
    ): ZIO[Any, HazzlenutError, LogWriter[ZIO[Any, HazzlenutError, ?]]] =
      (log4sFromName1 provide name)
        .mapError[HazzlenutError](error => ThrowableError(error))
  }
}

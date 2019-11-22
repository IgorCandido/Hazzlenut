package hazzlenut.services.twitch.actor.helper

import hazzlenut.HazzleNutZIO
import zio.DefaultRuntime

trait Executor[F[_]] {
  def runToCompletion[A](f: F[A]): A
}

object Executor{
  object dsl {
    implicit class ExecutorOps[A, F[_]](val f: F[A]) extends AnyVal{
      def runToCompletion(implicit executor: Executor[F]): A =
        executor.runToCompletion[A](f)
    }

    def runToCompletion[A, F[_]](f: F[A])(implicit executor: Executor[F]): A =
      executor.runToCompletion(f)
  }

  implicit def executorHazzleNutZio(implicit runTime: DefaultRuntime) = new Executor[HazzleNutZIO] {
    override def runToCompletion[A](f: HazzleNutZIO[A]): A =
      runTime.unsafeRun(f)
  }
}

package utils

import cats.MonadError
import cats.implicits._
import hazzlenut.errors.HazzlenutError
import hazzlenut.errors.HazzlenutError.{ThrowableError, UnableToConnect}
import org.scalatest.{AsyncWordSpec, Matchers, WordSpec}
import scalaz.zio.{DefaultRuntime, ZIO}
import scalaz.zio.interop.catz._

import scala.concurrent.Future
import scala.util.{Failure, Success}

class WidenFunctionSpec extends AsyncWordSpec with Matchers {

  "Widen functions with contravariance" should {
    "PartialFunction with contravariance" in {
      val f: PartialFunction[Throwable, HazzlenutError] = {
        case err => ThrowableError(err)
      }

      val monadError = implicitly[MonadError[ZIO[Any, Throwable, ?], Throwable]]

      val z: ZIO[Any, HazzlenutError, String] = ZIO.fail(UnableToConnect)

      val res = monadError.handleError(z)(_ => "Test")

      val adapted = monadError.adaptError(res)(f)

      val runtime = new DefaultRuntime {}

      runtime.unsafeRunToFuture(adapted.fold(err => fail("Not Handled"), _ should ===("Test")))
    }

    "PartialFunction with contravariance Future" in {

      val f: PartialFunction[Throwable, HazzlenutError] = {
        case err => ThrowableError(err)
      }

      val monadError = implicitly[MonadError[Future, Throwable]]

      val z = Future.failed(new Exception("Test"))

      val res = monadError.handleError(z)(_ => "Test")

      val adapted = monadError.adaptError(res)(f)

      adapted.transform { r =>
        r match {
          case Success(value)     => Success(succeed)
          case Failure(exception) => Failure(fail("Shouldn't have errored"))
        }
      }
    }

    "PartialFunction with contravariance Future handling" in {

      val f: PartialFunction[Throwable, HazzlenutError] = {
        case err => ThrowableError(err)
      }

      val handler: Throwable => String =  err => err match {
        case hazzlenutError: HazzlenutError => "HazzlenutError"
        case err: Throwable => "Throwable"
      }

      val monadError = implicitly[MonadError[Future, Throwable]]

      val z = Future.failed(UnableToConnect)

      val res = monadError.handleError(z)(handler)

      val adapted = monadError.adaptError(res)(f)

      adapted.transform { r =>
        r match {
          case Success(value)     => Success(value should===("HazzlenutError"))
          case Failure(exception) => Failure(fail("Shouldn't have errored"))
        }
      }
    }
  }

}

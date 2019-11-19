package hazzlenut.util

import java.io.{PrintWriter, StringWriter}

import cats.Show
import cats.implicits._

object ShowUtils {
  implicit val showThrowable = new Show[Throwable] {
    private def extractMessageFromThrowable(t: Throwable): Option[String] = {
      t.getMessage match {
        case null => None
        case message => message.some
      }
    }

    override def show(t: Throwable): String = {
      show"Throwable: message: ${extractMessageFromThrowable(t)}, stackTrace: ${getStackTraceAsString(t)}"
    }

    def getStackTraceAsString(t: Throwable): String = {
      val sw = new StringWriter()
      t.printStackTrace(new PrintWriter(sw))
      sw.toString()
    }
  }
}

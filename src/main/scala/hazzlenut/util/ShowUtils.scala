package hazzlenut.util

import java.io.{PrintWriter, StringWriter}

import cats.Show
import cats.implicits._

object ShowUtils {
  implicit val showThrowable = new Show[Throwable] {
    override def show(t: Throwable): String = {
      show"Throwable: message: ${t.getMessage}, stackTrace: ${getStackTraceAsString(t)}"
    }

    def getStackTraceAsString(t: Throwable): String = {
      val sw = new StringWriter()
      t.printStackTrace(new PrintWriter(sw))
      sw.toString()
    }
  }
}

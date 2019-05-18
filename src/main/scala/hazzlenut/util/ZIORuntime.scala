package hazzlenut.util

import scalaz.zio.DefaultRuntime

object ZIORuntime {
  lazy val runtime = new DefaultRuntime {}
}

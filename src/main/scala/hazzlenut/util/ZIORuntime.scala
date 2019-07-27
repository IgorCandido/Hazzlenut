package hazzlenut.util

import zio.DefaultRuntime

object ZIORuntime {
  lazy val runtime = new DefaultRuntime {}
}

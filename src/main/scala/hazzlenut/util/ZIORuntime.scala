package hazzlenut.util

import zio.DefaultRuntime

object ZIORuntime {
  implicit lazy val runtime = new DefaultRuntime {}
}

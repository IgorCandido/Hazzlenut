package hazzlenut.util

import log.effect.LogWriter
import log.effect.zio.ZioLogWriter.log4sFromName
import org.{log4s => l4s}
import zio.{IO, Task}

object Logging {

  implicit def log4ZIO(name: String): Task[LogWriter[Task]] = {
    log4sFromName provide name
  }
}

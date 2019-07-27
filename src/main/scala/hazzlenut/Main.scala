package hazzlenut

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import hazzlenut.akkaServer.Server
import hazzlenut.akkaServer.Server.Configuration
import hazzlenut.api.Authentication
import hazzlenut.errors.HazzlenutError
import hazzlenut.services.twitch.TokenGuardian.ApplicationStarted
import hazzlenut.services.twitch.TokenGuardian
import zio.ZIO
import cats.implicits._
import hazzlenut.util.Logging

object Main extends App {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher
  implicit val logger = Logging.log4ZIO("Hazzlenut")

  // TODO Think about using this actor system, same for akka http
  // and about import the twitch ZIO here.

  implicit val tokenGuardian = system.actorOf(TokenGuardian.props[ZIO[Any, HazzlenutError, ?]])

  val runConfiguration = Configuration(
    interface = "0.0.0.0",
    port = 8000,
    route = Authentication.publicRoute
  )

  tokenGuardian ! ApplicationStarted

  val server = Server.create.run(runConfiguration)

  println("Running")
}

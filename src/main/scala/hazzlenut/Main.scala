package hazzlenut

import hazzlenut.akkaServer.Server.Configuration
import hazzlenut.akkaServer.Server
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import hazzlenut.api.Authentication


object Main extends App {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher
  val runConfiguration = Configuration(interface= "0.0.0.0", port= 8000, route= Authentication.publicRoute)

  val server = Server.create.run(runConfiguration)

  println("Running")
}

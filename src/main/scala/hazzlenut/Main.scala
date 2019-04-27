package hazzlenut

import hazzlenut.akkaServer.Server.Configuration
import hazzlenut.akkaServer.{Api, Server}
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer


object Main extends App {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher
  val runConfiguration = Configuration(interface= "0.0.0.0", port= 8080, route= Api.route)

  val server = Server.create.run(runConfiguration)

  println("Running")
}

package hazzlenut

import hazzlenut.akkaServer.Server.Configuration
import hazzlenut.akkaServer.Server
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import hazzlenut.api.Authentication
import hazzlenut.services.twitch.{AkkaTokenHolderInitializer, TokenGuardian}


object Main extends App {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  implicit val tokenInitializer = AkkaTokenHolderInitializer()
  import hazzlenut.services.twitch.TwitchZIO._ // TODO Think about using this actor system, same for akka http
                                                // and about import the twitch ZIO here.

  val tokenGuardian = system.actorOf(TokenGuardian.props)

  val runConfiguration = Configuration(interface= "0.0.0.0", port= 8000, route= Authentication.publicRoute)

  val server = Server.create.run(runConfiguration)

  println("Running")
}

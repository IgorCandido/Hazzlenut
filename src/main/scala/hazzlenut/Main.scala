package hazzlenut

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import hazzlenut.akkaServer.Server
import hazzlenut.akkaServer.Server.Configuration
import hazzlenut.api.Authentication
import hazzlenut.services.twitch.actor.TokenGuardian.Message.ApplicationStarted
import hazzlenut.services.twitch.actor.helper.{
  FollowersInitializer,
  UserInfoInitializer
}
import hazzlenut.services.twitch.actor.{TokenGuardian, UserInfo}
import zio.interop.catz._
import hazzlenut.util.ZIORuntime._

object Main extends App {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  // TODO Think about using this actor system, same for akka http
  // and about import the twitch ZIO here.

  val tokenGuardian = system.actorOf(
    TokenGuardian.props[HazzleNutZIO](
      Seq(
        UserInfoInitializer.initializer[HazzleNutZIO],
        FollowersInitializer.initializer
      )
    )
  )

  val runConfiguration = Configuration(
    interface = "0.0.0.0",
    port = 8000,
    route = Authentication.publicRoute(tokenGuardian)
  )

  tokenGuardian ! ApplicationStarted

  val server = Server.create.run(runConfiguration)

  println("Running")
}

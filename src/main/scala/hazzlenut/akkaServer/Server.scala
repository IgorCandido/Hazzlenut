package hazzlenut.akkaServer

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import cats.data.Reader

import scala.concurrent.{ExecutionContext, Future}

object Server {

  case class Configuration(interface: String,
                           port: Int,
                           route: Route)

  def create(implicit system: ActorSystem,
          materializer: ActorMaterializer,
          executionContext: ExecutionContext) =
    Reader[Configuration, Future[Http.ServerBinding]] {
      configuration =>
        Http().bindAndHandle(configuration.route,
          interface = configuration.interface,
          port = configuration.port)
    }


}

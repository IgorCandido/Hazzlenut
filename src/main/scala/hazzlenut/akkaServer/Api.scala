package hazzlenut.akkaServer


import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.Credentials
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import scalaz.zio.DefaultRuntime

object Api {
  val route =
    pathPrefix("hello" / Segment) { name =>
      pathEnd {
        get {
          complete(HttpResponse(entity = HttpEntity(ContentTypes.`text/html(UTF-8)`,
            s"Hi ${name}")))
        }
      }
    }
}

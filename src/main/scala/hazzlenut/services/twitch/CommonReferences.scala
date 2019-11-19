package hazzlenut.services.twitch

import akka.actor.ActorSystem
import akka.stream.Materializer
import hazzlenut.util.{HttpClient, UnmarshallerEntiy}

case class CommonReferences[F[_]](implicit val actorSystem: ActorSystem,
                                  val materializer: Materializer,
                                  val httpClient: HttpClient[F],
                                  val unmarshallerEntiy: UnmarshallerEntiy[F])

object CommonReferences {
  implicit def buildCommonReferences[F[_]](
    implicit actorSystem: ActorSystem,
    materializer: Materializer,
    httpClient: HttpClient[F],
    unmarshallerEntiy: UnmarshallerEntiy[F]
  ) = CommonReferences()
}

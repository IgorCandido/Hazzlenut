package hazzlenut.services.twitch.helper

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

  object extractors {
    implicit def extractActorSystem[F[_]](
      implicit commonReferences: CommonReferences[F]
    ): ActorSystem = commonReferences.actorSystem

    implicit def extractMaterializer[F[_]](
      implicit commonReferences: CommonReferences[F]
    ): Materializer = commonReferences.materializer

    implicit def extractHttpClient[F[_]](
      implicit commonReferences: CommonReferences[F]
    ): HttpClient[F] = commonReferences.httpClient

    implicit def extractUnmarshallerEntity[F[_]](
      implicit commonReferences: CommonReferences[F]
    ): UnmarshallerEntiy[F] = commonReferences.unmarshallerEntiy
  }
}

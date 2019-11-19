package hazzlenut.services.twitch.model

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol

case class TwitchError(message: String, status: Int, error: String)

object TwitchError extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val twitchErrorFormat = jsonFormat3(TwitchError.apply)
}

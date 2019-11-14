package hazzlenut.services.twitch.model

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import hazzlenut.services.twitch.model.User.{jsonFormat1, jsonFormat10}
import spray.json.DefaultJsonProtocol

final case class Follow(from_id: String, from_name: String, to_id: String, to_name: String, followed_at: String)

object Follow extends SprayJsonSupport with DefaultJsonProtocol{
    implicit val followersReplyFormat = jsonFormat5(Follow.apply)
}


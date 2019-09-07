package hazzlenut.services.twitch.model

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol

case class User(broadcaster_type: String,
                description: String,
                display_name: String,
                email: Option[String],
                id: String,
                login: String,
                offline_image_url: String,
                profile_image_url: String,
                `type`: String,
                view_count: Int)

object User extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val userFormat = jsonFormat10(User.apply)
}

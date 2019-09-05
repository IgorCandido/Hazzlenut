package hazzlenut.services.twitch.model

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import hazzlenut.services.twitch.model.User.{jsonFormat1, jsonFormat10}
import spray.json.DefaultJsonProtocol

final case class FollowersReply(`_cursor`: String, `_total`: String, followers: Seq[User])

object FollowersReply extends SprayJsonSupport with DefaultJsonProtocol{
    implicit val followersReplyFormat = jsonFormat3(FollowersReply.apply)
}

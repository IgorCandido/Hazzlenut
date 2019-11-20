package hazzlenut.services.twitch.model

case class InternalRepresentationTwitchReply[T] (seq: Seq[T], pagination: Option[Pagination], total: Option[Long])

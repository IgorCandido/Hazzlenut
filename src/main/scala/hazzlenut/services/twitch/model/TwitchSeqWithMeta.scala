package hazzlenut.services.twitch.model

case class TwitchSeqWithMeta[T] (seq: Seq[T], pagination: Option[Pagination], total: Option[Long])
package hazzlenut.services.twitch.model

case class TwitchSeqWithMeta[T] (seq: Seq[T], pagination: Pagination, total: Long)
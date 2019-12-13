package hazzlenut.services.twitch.actor.model

trait CommonMessages

object CommonMessages{
  final case object ApplicationStarted extends CommonMessages
  final case object KillService extends CommonMessages
}

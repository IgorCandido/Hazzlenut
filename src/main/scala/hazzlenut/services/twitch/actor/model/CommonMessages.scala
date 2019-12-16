package hazzlenut.services.twitch.actor.model

trait CommonMessages

object CommonMessages{
  final case object ApplicationStarted extends CommonMessages
  final case object KillService extends CommonMessages

  object SupervisorThrowables{
    final case object ProperlyKilled extends Throwable {
      override def getMessage() = "Actor properly terminated signaling supervisor"
    }
  }
}

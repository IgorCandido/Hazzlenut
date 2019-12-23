package hazzlenut.services.twitch.actor.model

import hazzlenut.services.twitch.actor.TokenGuardian.ServiceType

trait CommonMessages

object CommonMessages{
  final case object ApplicationStarted extends CommonMessages
  final case object KillService extends CommonMessages

  object SupervisorThrowables{
    final case class ProperlyKilled(serviceType: ServiceType) extends Throwable {
      override def getMessage() = "Actor properly terminated signaling supervisor"
    }
  }
}

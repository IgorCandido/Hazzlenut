package hazzlenut.services.twitch.actor

import akka.actor.{Actor, ActorRef, PoisonPill, Props}
import cats.Monad
import hazzlenut.errors.HazzlenutError
import hazzlenut.handler.{AuthenticationHandler, TwitchClientHandler}
import hazzlenut.services.twitch.actor.TokenGuardian.{Service, ServiceInitializer, ServiceType}
import hazzlenut.services.twitch.actor.TokenGuardian.Message._
import hazzlenut.services.twitch.actor.adapter.TwitchClient
import hazzlenut.services.twitch.adapters.AccessToken
import hazzlenut.services.twitch.actor.helper.{Executor, TokenHolderInitializer, UserInfoInitializer}
import hazzlenut.util.{HttpClient, LogProvider}
import log.effect.LogLevels.Debug
import cats.implicits._
import hazzlenut.errors.HazzlenutError.UnableToAuthenticate
import hazzlenut.services.twitch.actor.helper.Executor.dsl._
import hazzlenut.services.twitch.actor.model.CommonMessages
import hazzlenut.services.twitch.actor.model.CommonMessages.ApplicationStarted
import log.effect.{LogLevel, LogLevels}

object TokenGuardian {
  val Name = "TokenGuardian"

  type Initializer = (ActorRef, ActorRef) => ActorRef

  object Message {
    case object CantRenewToken
    case class Authenticated(accessToken: AccessToken)
    case class RequireService(serviceType: ServiceType)
    case class ServiceProvide(serviceType: ServiceType, actorRef: ActorRef)
  }

  sealed trait ServiceType
  object ServiceType {
    final case object UserInfo extends ServiceType
    final case object Followers extends ServiceType
  }

  final case class Service(serviceType: ServiceType, actorRef: ActorRef)
  final case class ServiceInitializer(serviceType: ServiceType,
                                      initializer: Initializer)

  def props[F[_]: TwitchClientHandler: TwitchClient: HttpClient: LogProvider: Monad: Executor](
    serviceInitializers: Seq[ServiceInitializer]
  )(implicit authenticationHandler: AuthenticationHandler,
    tokenHolderInitializer: TokenHolderInitializer[F],
  ) =
    Props(new TokenGuardian(serviceInitializers))
}

class TokenGuardian[F[_]: TwitchClientHandler: TwitchClient: HttpClient: LogProvider: Executor](
  servicesInitializers: Seq[ServiceInitializer]
)(implicit authenticationHandler: AuthenticationHandler,
  tokenHolderInitializer: TokenHolderInitializer[F],
  monad: Monad[F])
    extends Actor {

  def poisonAllServices(services: Seq[Service]): Unit =
    services.foreach(_.actorRef ! PoisonPill)

  def initializeAllServices(
    tokenGuardian: ActorRef,
    tokenHolder: ActorRef,
    serviceInitializers: Seq[ServiceInitializer]
  ): Seq[Service] =
    serviceInitializers.map { sI =>
      Service(sI.serviceType, sI.initializer(tokenGuardian, tokenHolder))
    }

  def fulfillService(serviceType: ServiceType, services: Seq[Service]): Unit =
    services
      .find { service =>
        service.serviceType == serviceType
      }
      .fold(
        LogProvider
          .log[F](
            TokenGuardian.Name,
            LogLevels.Error,
            s"Service type ${serviceType} was required but not available"
          )
          .unsafeRun
      ) { service =>
        sender ! ServiceProvide(serviceType, service.actorRef)
      }

  def workingNormally(tokenHolder: ActorRef, services: Seq[Service]): Receive = {
    case RequireService(serviceType) =>
      fulfillService(serviceType, services)
    case CantRenewToken =>
      LogProvider
        .unsafeLogWithAction[F](
          TokenGuardian.Name,
          Debug,
          "Not able to renew token, reAuthenticating user"
        ) {
          authenticateUserAgainAndWaitForResult()

          // Kill TokenHolder
          tokenHolder ! PoisonPill
          // Kill Services
          poisonAllServices(services)
        }
    case msg @ (TokenHolder.TokenExpiredNeedNew | TokenHolder.AskAccessToken) =>
      tokenHolder forward msg
  }

  def waitingForUserAuthentication(
    queuedMessages: Set[(ActorRef, Any)] = Set.empty
  ): Receive = {
    case Authenticated(accessToken) =>
      val tokenHolder =
        tokenHolderInitializer.initializeTokenHolder(accessToken, self)
      // Notify new Token Holder the messages received whilst waiting
      queuedMessages.foreach {
        case (sender, msg) => tokenHolder.tell(msg, sender)
      }

      val services =
        initializeAllServices(self, tokenHolder, servicesInitializers)
      context.become(workingNormally(tokenHolder, services))
      services.foreach { _.actorRef ! CommonMessages.ApplicationStarted }

    case msg @ TokenHolder.AskAccessToken =>
      context.become(
        waitingForUserAuthentication(queuedMessages + ((sender(), msg)))
      )

    case TokenHolder.TokenExpiredNeedNew =>
      LogProvider
        .log[F](
          UserInfo.Name,
          Debug,
          "We are already handling the renewal of the oauth"
        )
        .unsafeRun
    // NOP we are already handling the renewal of the oauth
  }

  def authenticateUserAgainAndWaitForResult(): Either[HazzlenutError, Unit] = {
    // Asking for authentication
    context.become(waitingForUserAuthentication())
    authenticationHandler.reAuthenticate()
  }

  override def receive: Receive = {
    case ApplicationStarted =>
      authenticateUserAgainAndWaitForResult()
  }
}

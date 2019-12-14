package hazzlenut.services.twitch.actor

import akka.actor.SupervisorStrategy.Restart
import akka.actor.{Actor, ActorRef, OneForOneStrategy, PoisonPill, Props, SupervisorStrategy}
import akka.pattern.{BackoffOpts, BackoffSupervisor}
import cats.Monad
import hazzlenut.errors.HazzlenutError
import hazzlenut.handler.{AuthenticationHandler, TwitchClientHandler}
import hazzlenut.services.twitch.actor.TokenGuardian.Message._
import hazzlenut.services.twitch.actor.TokenGuardian.{Service, ServiceInitializer, ServiceType}
import hazzlenut.services.twitch.actor.adapter.TwitchClient
import hazzlenut.services.twitch.actor.helper.Executor.dsl._
import hazzlenut.services.twitch.actor.helper.{Executor, TokenHolderInitializer}
import hazzlenut.services.twitch.actor.model.CommonMessages
import hazzlenut.services.twitch.actor.model.CommonMessages.ApplicationStarted
import hazzlenut.services.twitch.adapters.AccessToken
import hazzlenut.util.{HttpClient, LogProvider}
import log.effect.LogLevels
import log.effect.LogLevels.Debug

import scala.concurrent.duration._

object TokenGuardian {
  val Name = "TokenGuardian"

  type Initializer = (Props => Props, ActorRef, ActorRef) => ActorRef

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

  val injectSupervisor: Props => Props = { childProps =>
    BackoffSupervisor.props(
      BackoffOpts
        .onFailure(
          childProps,
          childName = "myEcho",
          minBackoff = 30 milliseconds,
          maxBackoff = 1 seconds,
          randomFactor = 0.2 // adds 20% "noise" to vary the intervals slightly
        )
        .withSupervisorStrategy(OneForOneStrategy() {
          case _: Exception =>
            SupervisorStrategy.Restart // Dangerous to always restart for any exception
        })
    )
  }
}

class TokenGuardian[F[_]: TwitchClientHandler: TwitchClient: HttpClient: LogProvider: Executor](
  servicesInitializers: Seq[ServiceInitializer]
)(implicit authenticationHandler: AuthenticationHandler,
  tokenHolderInitializer: TokenHolderInitializer[F],
  monad: Monad[F])
    extends Actor {

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
      case _ => Restart
    }

  override def receive: Receive = {
    case ApplicationStarted =>
      authenticateUserAgainAndWaitForResult()
  }

  def poisonAllServices(services: Seq[Service]): Unit =
    services.foreach(_.actorRef ! PoisonPill)

  def initializeAllServices(
    tokenGuardian: ActorRef,
    tokenHolder: ActorRef,
    serviceInitializers: Seq[ServiceInitializer]
  ): Seq[Service] =
    serviceInitializers.map { sI =>
      Service(
        sI.serviceType,
        sI.initializer(
          TokenGuardian.injectSupervisor,
          tokenGuardian,
          tokenHolder
        )
      )
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
}

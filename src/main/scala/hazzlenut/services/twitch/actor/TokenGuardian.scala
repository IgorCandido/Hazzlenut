package hazzlenut.services.twitch.actor

import akka.actor.SupervisorStrategy.{Restart, Stop}
import akka.actor.{
  Actor,
  ActorContext,
  ActorRef,
  ActorSystem,
  OneForOneStrategy,
  PoisonPill,
  Props,
  SupervisorStrategy
}
import akka.pattern.{BackoffOpts, BackoffSupervisor}
import cats.Monad
import hazzlenut.errors.HazzlenutError
import hazzlenut.handler.{AuthenticationHandler, TwitchClientHandler}
import hazzlenut.services.twitch.actor.TokenGuardian.Message._
import hazzlenut.services.twitch.actor.TokenGuardian.{
  Service,
  ServiceInitializer,
  ServiceType
}
import hazzlenut.services.twitch.actor.adapter.TwitchClient
import hazzlenut.services.twitch.actor.helper.Executor.dsl._
import hazzlenut.services.twitch.actor.helper.{Executor, TokenHolderInitializer}
import hazzlenut.services.twitch.actor.model.CommonMessages
import hazzlenut.services.twitch.actor.model.CommonMessages.SupervisorThrowables.ProperlyKilled
import hazzlenut.services.twitch.actor.model.CommonMessages.{
  ApplicationStarted,
  KillService
}
import hazzlenut.services.twitch.adapters.AccessToken
import hazzlenut.util.{HttpClient, LogProvider}
import log.effect.LogLevels
import log.effect.LogLevels.Debug
import cats.implicits._

import scala.concurrent.duration._

object TokenGuardian {
  val Name = "TokenGuardian"

  type Initializer = (ActorContext, ActorRef, ActorRef) => ActorRef

  object Message {
    case object CantRenewToken
    case class Authenticated(accessToken: AccessToken)
    case class RequireService(serviceType: ServiceType)
    case class ServiceProvide(serviceType: ServiceType, actorRef: ActorRef)
    case class ServiceRemoved(serviceType: ServiceType)
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

  def handleServiceRemoved(
    services: Seq[Service]
  )(handlerCreator: Seq[Service] => Receive): Receive =
    handlerCreator(services) orElse {
      case ServiceRemoved(serviceType) =>
        context.become(
          handleServiceRemoved(
            services.filterNot(_.serviceType == serviceType)
          )(handlerCreator)
        )
    }

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
      case ProperlyKilled(serviceType) => {
        self ! ServiceRemoved(serviceType)
        Stop
      }
      case _ => Restart
    }

  override def receive: Receive = {
    case ApplicationStarted =>
      authenticateUserAgainAndWaitForResult()
  }

  def poisonAllServices(services: Seq[Service]): Unit =
    services.foreach(_.actorRef ! KillService)

  def initializeService(tokenGuardian: ActorRef,
                        tokenHolder: ActorRef,
                        serviceInitializer: ServiceInitializer): Service =
    Service(
      serviceInitializer.serviceType,
      serviceInitializer.initializer(context, tokenGuardian, tokenHolder)
    )

  def initializeAllServices(
    tokenGuardian: ActorRef,
    tokenHolder: ActorRef,
    serviceInitializers: Seq[ServiceInitializer]
  ): Seq[Service] =
    serviceInitializers.map { sI =>
      initializeService(tokenGuardian, tokenHolder, sI)
    }

  def fulfillService(serviceType: ServiceType,
                     tokenGuardian: ActorRef,
                     tokenHolder: ActorRef,
                     services: Seq[Service]): Seq[Service] = {
    case class ServicesAndService(services: Seq[Service],
                                  serviceMaybe: Option[Service])

    def createService: ServicesAndService = {
      servicesInitializers
        .find(_.serviceType == serviceType)
        .fold {
          LogProvider
            .log[F](
              TokenGuardian.Name,
              LogLevels.Error,
              s"Service type ${serviceType} was required but not available"
            )
            .unsafeRun
          ServicesAndService(services, None)
        } { serviceInitializer =>
          val service =
            initializeService(tokenGuardian, tokenHolder, serviceInitializer)
          ServicesAndService(
            services :+ initializeService(
              tokenGuardian,
              tokenHolder,
              serviceInitializer
            ),
            service.some
          )
        }
    }

    val servicesAndService = services
      .find(_.serviceType == serviceType)
      .fold(createService){service => ServicesAndService(services, service.some)}

    servicesAndService.serviceMaybe.foreach{ service =>
      sender ! ServiceProvide(serviceType, service.actorRef)
    }

    servicesAndService.services
  }

  def workingNormally(tokenHolder: ActorRef)(services: Seq[Service]): Receive = {
    case RequireService(serviceType) =>
      fulfillService(serviceType, self, tokenHolder, services)
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
      context.become(
        handleServiceRemoved(services)(workingNormally(tokenHolder))
      )
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

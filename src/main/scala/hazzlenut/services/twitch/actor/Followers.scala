package hazzlenut.services.twitch.actor

import akka.actor.{ActorRef, PoisonPill, Props, Status}
import akka.pattern.pipe
import akka.persistence.{PersistentActor, SnapshotOffer}
import akka.stream.ActorMaterializer
import cats.Monad
import cats.implicits._
import hazzlenut.handler.TwitchClientHandler
import hazzlenut.handler.TwitchClientHandler.dsl.retrieveFollowers
import hazzlenut.services.twitch.actor.Followers._
import hazzlenut.services.twitch.actor.TokenGuardian.Message.{RequireService, ServiceProvide}
import hazzlenut.services.twitch.actor.TokenGuardian.ServiceType
import hazzlenut.services.twitch.actor.TokenHolder.ReplyAccessToken
import hazzlenut.services.twitch.actor.UserInfo.{ProvideUser, RetrieveUser}
import hazzlenut.services.twitch.actor.adapter.{TokenHolderApi, TwitchClient}
import hazzlenut.services.twitch.actor.helper.Executor
import hazzlenut.services.twitch.actor.helper.Executor.dsl._
import hazzlenut.services.twitch.actor.model.CommonMessages
import hazzlenut.services.twitch.actor.model.CommonMessages.KillService
import hazzlenut.services.twitch.actor.model.CommonMessages.SupervisorThrowables.ProperlyKilled
import hazzlenut.services.twitch.adapters.AccessToken
import hazzlenut.services.twitch.model.User
import hazzlenut.util.{HttpClient, LogProvider, UnmarshallerEntiy}
import log.effect.LogLevels

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

object Followers {
  val Name = "Followers"

  def props[F[_]: Monad: TwitchClientHandler: TwitchClient: HttpClient: UnmarshallerEntiy: LogProvider: Executor](
    tokenGuardian: ActorRef,
    tokenHolder: ActorRef,
    pollingPeriod: FiniteDuration
  ): Props =
    Props(new Followers[F](tokenGuardian, tokenHolder, pollingPeriod))

  final case object RetrieveFollowers

  final case class ProvideFollowers(followers: Seq[Follower])

  final case object PollFollowers

  final case class ResultPollFollowers(followers: Seq[Follower],
                                       cursor: String,
                                       total: Long)

  final case class Follower(userName: String, followedAt: String)

  // Merge list
  def mergeFollowers(existingFollowers: Seq[Follower],
                     newFollowers: Seq[Follower]): Seq[Follower] = {
    def loop(existing: Seq[Follower],
             newFollowers: Seq[Follower]): Seq[Follower] =
      newFollowers match {
        case Nil => existing
        case h :: tail if existing exists (_.userName == h.userName) =>
          loop(existing, tail)
        case h :: tail => loop(existing :+ h, tail)
      }

    loop(existingFollowers, newFollowers)
  }

  final case class CursorUpdated(cursor: String)

  final case object CursorCleaned

  final case object Bootstrap
}

class Followers[F[_]: Monad: TwitchClientHandler: TwitchClient: HttpClient: UnmarshallerEntiy: LogProvider: Executor](
  tokenGuardian: ActorRef,
  tokenHolder: ActorRef,
  pollingPeriod: FiniteDuration
) extends PersistentActor {
  implicit val system = context.system
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  import TokenHolderApi._

  override val persistenceId: String = Followers.Name
  var cursorState: Option[String] = None

  def handlePoisonPill: Receive = {
    case KillService =>
      persist(CursorCleaned) { _ => // Actor being reset cleans cursor
        cursorState = None
        throw ProperlyKilled
      }
  }

  def withDefaultHandling(receiveHandler: Receive): Receive =
    receiveHandler orElse handlePoisonPill

  def fetchToken(userInfo: ActorRef, expiredAccessToken: Boolean = false) =
    fetchAccessToken(
      waitingForToken(userInfo),
      tokenHolder,
      self,
      expiredAccessToken
    )

  def waitingForToken(userInfo: ActorRef): Receive = {
    case ReplyAccessToken(accessToken) => {
      // Fetch the user from the UserInfo
      context.become(withDefaultHandling(waitingForUserInfo(accessToken)))
      userInfo ! RetrieveUser
    }
  }

  def waitingForUserInfo(accessToken: AccessToken): Receive = {
    case ProvideUser(user) => {
      context.become(
        withDefaultHandling(
          pollFollowers(accessToken, user, Seq.empty, cursorState, 0)
        )
      )
      self ! PollFollowers
    }
  }

  // Retrieve list of followers
  def poll(accessToken: AccessToken, user: User, cursor: Option[String])(
    implicit executionContext: ExecutionContext
  ): Future[ResultPollFollowers] =
    retrieveFollowers(accessToken, user.id, cursor).map(
      followers =>
        ResultPollFollowers(
          followers.seq.map(f => Follower(f.to_name, f.followed_at)),
          followers.pagination.cursor,
          followers.total
      )
    )

  def pollFollowers(accessToken: AccessToken,
                    user: User,
                    followers: Seq[Follower],
                    cursor: Option[String],
                    total: Long): Receive = {
    case RetrieveFollowers =>
      sender ! ProvideFollowers(followers)
    case PollFollowers =>
      poll(accessToken, user, cursor) pipeTo self
    case ResultPollFollowers(newFollowers, cursor, total) => {
      persist(CursorUpdated(cursor)) { cursorUpdated =>
        context.become(
          withDefaultHandling(
            pollFollowers(
              accessToken,
              user,
              mergeFollowers(
                existingFollowers = followers,
                newFollowers = newFollowers
              ),
              cursorUpdated.cursor.some,
              total
            )
          )
        )
        cursorState = cursorUpdated.cursor.some
        system.scheduler.scheduleOnce(pollingPeriod) {
          self ! PollFollowers
        }
      }
    }
    case Status.Failure(failure) =>
      LogProvider
        .log[F](Name, LogLevels.Error, "failed to retrieve followers")
        .unsafeRun
  }

  private def bootStrap: Receive = {
    case Bootstrap => startActor()
  }

  private def startActor() = {
    context.become(withDefaultHandling(waitingForUserInfo))
    tokenGuardian ! RequireService(ServiceType.UserInfo)
  }

  override def receiveCommand: Receive =
    withDefaultHandling({
      case CommonMessages.ApplicationStarted =>
        startActor()
    })

  def waitingForUserInfo: Receive = {
    case ServiceProvide(ServiceType.UserInfo, userInfoRef) =>
      fetchToken(userInfoRef)
  }

  override def receiveRecover
    : Receive = { // Fully aware that the actor will require multiple times the UserInfo service
    case CursorUpdated(cursorValue) => {
      cursorState = cursorValue.some
      context.become(bootStrap)
      self ! Bootstrap
    }
    case SnapshotOffer(_, c: String) => {
      cursorState = c.some
      context.become(bootStrap)
      self ! Bootstrap
    }
    case CursorCleaned => {
      cursorState = None
      context.become(receiveCommand)
    }
  }
}

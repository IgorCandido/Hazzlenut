package hazzlenut.services.twitch.actor

import akka.actor.{Actor, ActorRef, Props, Status}
import akka.event.Logging.LogLevel
import akka.pattern.pipe
import akka.stream.ActorMaterializer
import cats.Monad
import hazzlenut.handler.TwitchClientHandler
import hazzlenut.handler.TwitchClientHandler.dsl.retrieveFollowers
import hazzlenut.services.twitch.actor.Followers._
import hazzlenut.services.twitch.actor.TokenHolder.ReplyAccessToken
import hazzlenut.services.twitch.actor.UserInfo.{ProvideUser, RetrieveUser}
import hazzlenut.services.twitch.actor.adapter.{TokenHolderApi, TwitchClient}
import hazzlenut.services.twitch.model.User
import hazzlenut.services.twitch.adapters.AccessToken
import hazzlenut.util.{HttpClient, LogProvider, UnmarshallerEntiy}
import cats.implicits._
import hazzlenut.services.twitch.actor.helper.Executor
import log.effect.LogLevels
import hazzlenut.services.twitch.actor.helper.Executor.dsl._

import scala.concurrent.{ExecutionContext, Future}

object Followers {
  val Name = "Followers"

  def props[F[_]: Monad: TwitchClientHandler: TwitchClient: HttpClient: UnmarshallerEntiy: LogProvider: Executor](
    tokenHolder: ActorRef,
    userInfo: ActorRef
  ): Props =
    Props(new Followers[F](tokenHolder, userInfo))

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
    def loop(existing: Seq[Follower], newFollowers: Seq[Follower]): Seq[Follower] =
      newFollowers match {
        case Nil => existing
        case h :: tail if existing exists (_.userName == h.userName) => loop(existing, tail)
        case h :: tail => loop(existing :+ h, tail)
      }
    loop(existingFollowers, newFollowers)
  }
}

class Followers[F[_]: Monad: TwitchClientHandler: TwitchClient: HttpClient: UnmarshallerEntiy: LogProvider: Executor](
  tokenHolder: ActorRef,
  userInfo: ActorRef
) extends Actor {
  implicit val system = context.system
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher
  import TokenHolderApi._

  override def receive: Receive = Actor.emptyBehavior

  def fetchToken(expiredAccessToken: Boolean = false) =
    fetchAccessToken(waitingForToken, tokenHolder, self, expiredAccessToken)

  def waitingForToken: Receive = {
    case ReplyAccessToken(accessToken) => {
      // Fetch the user from the UserInfo
      context.become(waitingForUserInfo(accessToken))
      userInfo ! RetrieveUser
    }
  }

  def waitingForUserInfo(accessToken: AccessToken): Receive = {
    case ProvideUser(user) => {
      context.become(pollFollowers(accessToken, user, Seq.empty, None, 0))
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
    case ResultPollFollowers(newFollowers, cursor, total) =>
      context.become(
        pollFollowers(
          accessToken,
          user,
          mergeFollowers(existingFollowers = followers, newFollowers = newFollowers),
          cursor.some,
          total
        )
      )
    case Status.Failure(failure) =>
      LogProvider.log[F](Name, LogLevels.Error, "failed to retrieve followers").unsafeRun
  }
}

package hazzlenut.services.twitch.actor

import akka.actor.{Actor, ActorRef, Props, Status}
import akka.pattern.pipe
import akka.stream.ActorMaterializer
import hazzlenut.handler.TwitchClientHandler
import hazzlenut.handler.TwitchClientHandler.dsl.retrieveFollowers
import hazzlenut.services.twitch.actor.Followers._
import hazzlenut.services.twitch.actor.TokenHolder.ReplyAccessToken
import hazzlenut.services.twitch.actor.UserInfo.{ProvideUser, RetrieveUser}
import hazzlenut.services.twitch.actor.adapter.{TokenHolderApi, TwitchClient}
import hazzlenut.services.twitch.model.User
import hazzlenut.services.twitch.adapters.AccessToken
import hazzlenut.util.{HttpClient, UnmarshallerEntiy}
import cats.implicits._

import scala.concurrent.{ExecutionContext, Future}

object Followers {
  def props[F[_]: TwitchClientHandler: TwitchClient: HttpClient: UnmarshallerEntiy](
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
}

class Followers[F[_]: TwitchClientHandler: TwitchClient: HttpClient: UnmarshallerEntiy](
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
      context.become(pollFollowers(accessToken, user, Seq.empty))
      self ! PollFollowers
    }
  }

  // Merge list
  def handle(existingFollowers: Set[Follower],
             newFollowers: Seq[Follower]): Seq[Follower] = {
    def loop(existing: Set[Follower], newFollowers: Seq[Follower]): List[Follower] =
      newFollowers match {
        case h :: tail if existing contains h => loop(existing, tail)
        case h :: tail => h :: loop(existing + h, tail)
      }
    loop(existingFollowers, newFollowers)
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
          handle(existingFollowers = followers, newFollowers = newFollowers),
          cursor.some,
          total
        )
      )
    case Status.Failure(failure) =>
  }
}

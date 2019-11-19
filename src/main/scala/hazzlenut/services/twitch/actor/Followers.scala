package hazzlenut.services.twitch.actor

import akka.actor.{Actor, ActorRef, Props, Status}
import akka.pattern.pipe
import akka.stream.ActorMaterializer
import hazzlenut.services.twitch.actor.Followers._
import hazzlenut.services.twitch.actor.TokenHolder.ReplyAccessToken
import hazzlenut.services.twitch.actor.UserInfo.{ProvideUser, RetrieveUser}
import hazzlenut.services.twitch.actor.adapter.TokenHolderApi
import hazzlenut.services.twitch.model.User
import hazzlenut.services.twitch.adapters.AccessToken

import scala.concurrent.{ExecutionContext, Future}

object Followers {
  def props(tokenHolder: ActorRef, userInfo: ActorRef): Props =
    Props(new Followers(tokenHolder, userInfo))

  final case object RetrieveFollowers
  final case class ProvideFollowers(followers: Seq[Follower])
  final case object PollFollowers
  final case class ResultPollFollowers(followers: Seq[Follower])

  final case class Follower(userName: String)
}

class Followers(tokenHolder: ActorRef, userInfo: ActorRef) extends Actor {
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
  def handle(existingFollowers: Seq[Follower],
             newFollowers: Seq[Follower]): Seq[Follower] = {
    Seq.empty
  }

  // Retrieve list of followers
  def poll(accessToken: AccessToken, user: User)(
    implicit executionContext: ExecutionContext
  ): Future[ResultPollFollowers] = {
    Future(ResultPollFollowers(Seq.empty))
  }

  def pollFollowers(accessToken: AccessToken,
                    user: User,
                    followers: Seq[Follower]): Receive = {
    case RetrieveFollowers =>
      sender ! ProvideFollowers(followers)
    case PollFollowers =>
      poll(accessToken, user) pipeTo self
    case ResultPollFollowers(newFollowers) =>
      context.become(
        pollFollowers(
          accessToken,
          user,
          handle(existingFollowers = followers, newFollowers = newFollowers)
        )
      )
    case Status.Failure(failure) =>
  }
}

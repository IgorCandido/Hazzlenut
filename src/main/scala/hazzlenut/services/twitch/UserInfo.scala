package hazzlenut.services.twitch

import akka.actor.{Actor, ActorRef, Props}
import akka.pattern.pipe
import akka.stream.ActorMaterializer
import hazzlenut.handler.TwitchClientHandler.dsl._
import hazzlenut.services.twitch.TokenHolder.{AskAccessToken, ReplyAccessToken}
import hazzlenut.services.twitch.UserInfo.{ProvideUser, RetrieveUserId}
import hazzlenut.services.twitch.model.User

import scala.util.Success

object UserInfo{
  def props(tokenHolder: ActorRef) = Props(new UserInfo(tokenHolder))

  case object RetrieveUserId
  case class ProvideUser(user: User)
}

// Killed when TokenHolder is killed in order to reAuthenticated
// Created as soon as the Token is retrieved for a TokenHolder
class UserInfo(tokenHolder: ActorRef) extends Actor {
  implicit val system = context.system
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  fetchUser()

  override def receive: Receive = ???

  def providingUser(user: User) : Receive= {
    case RetrieveUserId =>
      sender ! ProvideUser(user)
  }

  def waitingForToken : Receive = {
    case ReplyAccessToken(accessToken) =>
      context.become(waitingForUser)
      retrieveUser(accessToken) pipeTo self
  }

  def waitingForUser : Receive = {
    case Success(user: User) => context.become(providingUser(user))
    case _ => // What to do when failure on getting User
              // 1 - Retry
              // 2 - Close app
              // 3 - ReAuthenticate
  }

  def fetchUser() ={
    context.become(waitingForToken)
    tokenHolder ! AskAccessToken
  }
}

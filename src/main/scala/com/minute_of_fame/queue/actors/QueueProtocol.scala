package com.minute_of_fame.queue.actors

import akka.actor.Status.{Failure, Success}
import akka.pattern.ask
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.util.Timeout
import com.minute_of_fame.queue.actors.QueueProtocol._
import com.minute_of_fame.queue.models.DbModels.AuthUser
import com.minute_of_fame.queue.models.JsonPackets.{AddToQueue, CommandPacket}
import io.circe.parser.decode

import scala.collection.mutable
import scala.concurrent.duration._

object QueueProtocol {
  def props(db: ActorRef, qhandler: ActorRef) = Props(classOf[QueueProtocol], db, qhandler)

  case class QueueUser()
  case class AuthQueueUser(user: AuthUser) extends QueueUser
  case class QueuePublisher(streamId: String, override val user: AuthUser) extends AuthQueueUser(user)
}

class QueueProtocol(db: ActorRef, qhandler: ActorRef) extends Actor with ActorLogging {
  implicit val timeout = Timeout(2 seconds)

  private val users = mutable.HashMap[Int, QueueUser]()

  private var session: ActorRef = _

  def make_packet()

  override def receive: Receive = {
    case "connected" =>
      sender() ! packets.Register(name = "queue")

    case pack: packets.Packet =>
      pack.data match {
        case "connected" =>
          session = sender()
          if(pack.isAuth) {
            ask(db, DataBase.GetUser(pack.userId)) {
              case Success(DataBase.UserInfo(user)) =>
                qhandler ! QueueHandler.Connected(pack.userId)
                users += (pack.userId -> AuthQueueUser(user))

              case Failure(exception) => log.warning("Cant get auth user {}:\n {}", pack.userId, exception)
            }
          } else users += (pack.userId -> QueueUser())

        case "disconnected" =>
          if(pack.isAuth)
            qhandler ! QueueHandler.Disconnected(pack.userId)
          users -= pack.userId

        case json =>
          decode[CommandPacket](json) match {
            case Right(cmd) =>
              cmd.command match {
                case "queue" =>
                  decode[AddToQueue](cmd.data) match {
                    case Right(queuePack) =>
                      qhandler ! QueueHandler.AddToQueue(pack.userId)
                      users(pack.userId) = QueuePublisher(queuePack.id, users(pack.userId).asInstanceOf[AuthUser])

                    case Left(err) =>
                      log.error("Cant decode json packet {}:\n{}", json, err)
                  }
                case "stop" =>
                  users(pack.userId) match {
                    case publisher: QueuePublisher =>
                      qhandler ! QueueHandler.StopStream(pack.userId)
                      users(pack.userId) = AuthQueueUser(publisher.user)
                    case _ =>
                      log.error("Cannot stop non existing stream for pack {}", pack)
                  }
              }
            case Left(err) =>
              log.error("Cant decode command json packet {}:\n{}", json, err)
          }
      }
    case QueueHandler.SetTime =>
      users.foreach(session ! packets.Packet)
  }
}

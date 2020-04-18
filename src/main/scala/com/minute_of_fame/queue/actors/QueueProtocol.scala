package com.minute_of_fame.queue.actors

import scala.util.{Failure, Success}
import akka.pattern.ask
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.util.Timeout
import com.minute_of_fame.queue.actors.QueueProtocol._
import com.minute_of_fame.queue.models.DbModels.AuthUser
import com.minute_of_fame.queue.models.JsonPackets.{AddToQueue, Command, CommandPacket, SetStream, SetTime, StopStream, UpdatePlace}
import com.minute_of_fame.queue.models.JsonPackets.CommandPacketDecoder._
import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._

import scala.collection.mutable
import scala.concurrent.duration._

object QueueProtocol {
  def props(db: ActorRef, qhandler: ActorRef) = Props(classOf[QueueProtocol], db, qhandler)

  sealed abstract class StreamViewer
  case class QueueViewer() extends StreamViewer
  abstract class AuthViewer(user: AuthUser) extends StreamViewer
  case class AuthQueueViewer(user: AuthUser) extends AuthViewer(user)
  case class QueuePublisher(streamId: String, user: AuthUser) extends AuthViewer(user)
}

class QueueProtocol(db: ActorRef, qhandler: ActorRef) extends Actor with ActorLogging {
  implicit val timeout = Timeout(2.seconds)
  import context._

  private val users = mutable.HashMap[Int, StreamViewer]()

  private var session: ActorRef = _

  def packCommand(id: Int, name: String, cmd: Command) =
    packets.Packet(id, data=CommandPacket(name, cmd).asJson.noSpaces)

  override def receive: Receive = {
    case "connected" =>
      sender() ! packets.Register(name = "queue")
      qhandler ! "connected"

    case pack: packets.Packet =>
      pack.data match {
        case "connected" =>
          session = sender()
          if(pack.isAuth) {
            db ? DataBase.GetUser(pack.userId) onComplete {
              case Success(DataBase.UserInfo(user)) =>
                log.info("Auth complete id: {} \n {}", pack.userId, user)
                users += (pack.userId -> AuthQueueViewer(user))

              case Failure(exception) => log.error("Cant get auth user {}:\n {}", pack.userId, exception)

              case other => log.error("Received unsupported model {}", other)
            }
          } else users += (pack.userId -> QueueViewer())
          log.info("User {} successfully added, users {}", pack.userId, users.keys.toString)
          qhandler ! QueueHandler.Connected(pack.userId)

        case "disconnected" =>
          if(pack.isAuth)
            qhandler ! QueueHandler.Disconnected(pack.userId)
          users -= pack.userId
          log.info("User {} disconnected", pack.userId)

        case json =>
          import com.minute_of_fame.queue.models.JsonPackets.CommandPacketDecoder._
          decode[CommandPacket](json) match {
            case Right(cmd) =>
              cmd.data match {
                case queuePack: AddToQueue =>
                  qhandler ! QueueHandler.AddToQueue(pack.userId)
                  users(pack.userId) = QueuePublisher(queuePack.id, users(pack.userId).asInstanceOf[AuthQueueViewer].user)

                case StopStream() =>
                  users(pack.userId) match {
                    case publisher: QueuePublisher =>
                      qhandler ! QueueHandler.StopStream(pack.userId)
                      users(pack.userId) = AuthQueueViewer(publisher.user)
                    case _ =>
                      log.error("Cannot stop non existing stream for pack {}", pack)
                  }
              }

            case Left(err) =>
              log.error("Cant decode command json packet {}:\n{}", json, err)
          }
      }

    case QueueHandler.SetTime(time) =>
      log.info("Users {}", users.keys.mkString(" "))
      users.keys.foreach(session ! packCommand(_, "set_time", SetTime(time)))

    case QueueHandler.SetStream(stream, userId) =>
      users.get(stream) match {
        case Some(QueuePublisher(streamId, publisher)) =>
          log.info("Set stream {} for {}", streamId, userId)
          log.info("Users {}", users.keys.toList)
          if(userId >= 0) session ! packCommand(userId, "set_stream", SetStream(streamId))
          else users.keys
            .filter(_ != publisher.id).foreach(session ! packCommand(_, "set_stream", SetStream(streamId)))
        case _ =>
          log.error("Can't find publisher with id {}", stream)
      }

    case QueueHandler.UpdatePlaces(queue) =>
      users.keys.foreach(session ! packCommand(_, "update_places", UpdatePlace(queue)))
  }
}

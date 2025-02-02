package com.minute_of_fame.queue.actors

import scala.util.{Failure, Success}
import akka.pattern.ask
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.util.Timeout
import com.minute_of_fame.queue.actors.DataBase.{SaveStream, SaveViews}
import com.minute_of_fame.queue.actors.QueueHandler.UpdateViewers
import com.minute_of_fame.queue.actors.QueueProtocol._
import com.minute_of_fame.queue.models.DbModels.{AppStream, AuthUser}
import com.minute_of_fame.queue.models.JsonPackets
import com.minute_of_fame.queue.models.JsonPackets.{AddToQueue, Command, CommandPacket, SetRtcStream, SetStream, SetTime, StopStream, UpdatePlace}
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

  abstract class AuthViewer(val user: AuthUser) extends StreamViewer

  case class AuthQueueViewer(override val user: AuthUser) extends AuthViewer(user)

  case class StreamInfo(streamId: String, title: String, description: String)

  case class QueuePublisher(streamInfo: StreamInfo, override val user: AuthUser) extends AuthViewer(user)

}

class QueueProtocol(db: ActorRef, qhandler: ActorRef) extends Actor with ActorLogging {
  implicit val timeout = Timeout(2.seconds)

  import context._

  private val users = mutable.HashMap[Int, StreamViewer]()
  private var maxViews = 0

  private var session: ActorRef = _

  def packCommand(id: Int, name: String, cmd: Command) =
    packets.Packet(id, data = CommandPacket(name, cmd).asJson.noSpaces)

  override def receive: Receive = {
    case "connected" =>
      sender() ! packets.Register(name = "queue")
      qhandler ! "connected"

    case pack: packets.Packet =>
      pack.data match {
        case "connected" =>
          session = sender()
          if (pack.isAuth) {
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
          if (pack.isAuth)
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
                  users(pack.userId) =
                    QueuePublisher(StreamInfo(queuePack.id,
                      queuePack.title, queuePack.description), users(pack.userId).asInstanceOf[AuthViewer].user)

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
        case Some(QueuePublisher(streamInfo, newPublisher)) =>
          log.info("Set stream {} for {}", streamInfo.streamId, userId)
          log.info("Users {}", users.keys.toList)
          db ! SaveViews(maxViews)
          maxViews = 0
          db ! SaveStream(AppStream(
            streamId = streamInfo.streamId,
            publisherId = newPublisher.id,
            title = streamInfo.title,
            description = streamInfo.description))
          session ! packets.InternalPacket(message = CommandPacket("set_rtc_stream", SetRtcStream(newPublisher.id)).asJson.noSpaces)
          val com = SetStream(streamInfo.streamId, newPublisher.username, streamInfo.title, streamInfo.description)
          if (userId >= 0)
            session ! packCommand(userId,
              "set_stream", com)
          else users.keys.foreach(session ! packCommand(_, "set_stream", com))
        case _ =>
          log.error("Can't find publisher with id {}", stream)
      }

    case QueueHandler.UpdatePlaces(queue) =>
      users.keys.foreach(session ! packCommand(_, "update_places",
        UpdatePlace(queue.map(id => users.get(id).map(_.asInstanceOf[QueuePublisher].user.username).getOrElse("")))))

    case QueueHandler.UpdateViewers(count) =>
      maxViews = maxViews.max(users.size)
      users.keys.foreach(session ! packCommand(_, "update_viewers", JsonPackets.UpdateViewers(users.size)))
  }
}

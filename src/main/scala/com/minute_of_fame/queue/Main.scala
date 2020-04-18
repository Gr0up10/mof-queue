package com.minute_of_fame.queue

import java.net.InetSocketAddress

import akka.actor.ActorSystem
import com.minute_of_fame.queue.actors.QueueHandler.Tick
import com.minute_of_fame.queue.actors.{Client, DataBase, QueueHandler, QueueProtocol, Session}

import scala.concurrent.duration._

object Main extends App {
  implicit val actorSystem: ActorSystem = ActorSystem()
  import actorSystem.dispatcher

  var db = actorSystem.actorOf(DataBase.props)
  var handler = actorSystem.actorOf(QueueHandler.props(db), "queue_handler")
  val protocol = actorSystem.actorOf(QueueProtocol.props(db, handler), "protocol")
  val session = actorSystem.actorOf(Session.props(protocol), "session")
  val host = scala.util.Properties.envOrElse("HANDLER_HOST", "localhost")
  val port = scala.util.Properties.envOrElse("HANDLER_PORT", "58008").toInt
  println("Connecting to "+host)
  val mainActor = actorSystem.actorOf(Client.props(new InetSocketAddress(host, port), session), "client")

  val cancellable =
    actorSystem.scheduler.scheduleWithFixedDelay(Duration.Zero, 1.second, handler, Tick())
}

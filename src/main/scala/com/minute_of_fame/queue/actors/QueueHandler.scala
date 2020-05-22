package com.minute_of_fame.queue.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

object QueueHandler {
  def props(db: ActorRef, streamTime: Int = 40) = Props(classOf[QueueHandler], db, streamTime)

  case class Connected(id: Int)
  case class AddToQueue(id: Int)
  case class StopStream(id: Int)
  case class Disconnected(id: Int)
  case class Tick()

  case class SetStream(streamId: Int, userId: Int = -1)
  case class UpdatePlaces(queue: Array[Int])
  case class SetTime(time: Int)
  case class UpdateViewers(count: Int)
}

class QueueHandler(db: ActorRef, streamTime: Int) extends Actor with ActorLogging {
  import QueueHandler._

  private var protocol: ActorRef = _

  private val clients = ArrayBuffer[Int]()
  private val queue = mutable.Queue[Int]()
  private var currentTime = 0
  private var currentStream = -1
  private var prevViewers = 0

  private def updatePlaces(): Unit =
    protocol ! UpdatePlaces(queue.toArray)

  def selectNext(): Unit = {
    if (queue.nonEmpty) {
      currentTime = streamTime
      val next = queue.dequeue()
      currentStream = next
      protocol ! SetStream(next)
      updatePlaces()
      log.info("Select {}", next)
    } else {
      currentStream = -1
    }
  }

  override def receive: Receive = {
    case "connected" => protocol = sender()

    case Connected(id) =>
      clients += id
      if(currentStream > 0) protocol ! SetStream(currentStream, id)

    case Disconnected(id) =>
      clients -= id
      queue.dequeueFirst(_ == id)
      if(id == currentStream) selectNext()
      else updatePlaces()

    case AddToQueue(stream) =>
      log.info(s"Added to queue $stream")
      queue += stream
      updatePlaces()

    case StopStream(id) =>
      queue.dequeueFirst(_ == id)
      if(id == currentStream) selectNext()
      else updatePlaces()

    case Tick() =>
      if(currentTime == 0) {
        selectNext()
      }

      if(currentStream > 0) {
        protocol ! SetTime(currentTime)
        currentTime -= 1
      }

      if(prevViewers != clients.length) {
        protocol ! UpdateViewers(clients.length)
        prevViewers = clients.length
      }
  }
}

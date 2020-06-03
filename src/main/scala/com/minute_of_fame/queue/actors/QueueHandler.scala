package com.minute_of_fame.queue.actors

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.{Failure, Success}

object QueueHandler {
  def props(db: ActorRef, streamTime: Int = 60) = Props(classOf[QueueHandler], db, streamTime)

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
  implicit val timeout: Timeout = Timeout(3, TimeUnit.SECONDS)
  import context._

  private var protocol: ActorRef = _

  private val clients = ArrayBuffer[Int]()
  private val queue = mutable.Queue[Int]()
  private var currentTime = 0
  private var currentStream = -1
  private var prevViewers = 0

  private def updatePlaces(): Unit =
    protocol ! UpdatePlaces(queue.toArray)

  def selectNext(force: Boolean = false): Unit = {
    db ? DataBase.GetCurrentLikeDislikeRatio() onComplete {
      case Success(ratio: Double) =>
        log.info("Like/Dislike = {}", ratio)
        if(ratio < 0.5 || force) {
          if (queue.nonEmpty) {
            currentTime = streamTime
            val next = queue.dequeue()
            currentStream = next
            protocol ! SetStream(next)
            updatePlaces()
            log.info("Select {}", next)
          } else {
            log.info("No more people in the queue")
            currentStream = -1
          }
        } else currentTime = streamTime
      case Failure(exception) => log.error("Exception while getting ratio {}", exception)
      case other => log.error("Got incorrect data type {}", other)
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
      if(id == currentStream) selectNext(true)
      else updatePlaces()

    case AddToQueue(stream) =>
      log.info(s"Added to queue $stream")
      queue += stream
      updatePlaces()
      if(currentStream == -1) selectNext(true)

    case StopStream(id) =>
      queue.dequeueFirst(_ == id)
      if(id == currentStream) selectNext(true)
      else updatePlaces()

    case Tick() =>
      if(currentTime == 0 && currentStream != -1) {
        selectNext()
      }

      if(currentStream > 0) {
        protocol ! SetTime(currentTime)
        currentTime -= 1
        log.info("Current time: {}; Stream id: {}; Queue: {}", currentTime, currentStream, queue.mkString)
      }

      if(prevViewers != clients.length) {
        protocol ! UpdateViewers(clients.length)
        prevViewers = clients.length
      }
  }
}

package com.minute_of_fame.queue.models

object JsonPackets {
  case class CommandPacket(command: String, data: String)

  abstract class Command
  case class AddToQueue(streamType: String, id: String) extends Command

  case class SetTime(time: Int) extends Command
  case class UpdatePlace(queue: Array[Int]) extends Command
  case class SetStream(id: String) extends Command
}
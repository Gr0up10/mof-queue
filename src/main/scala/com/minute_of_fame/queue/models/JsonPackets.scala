package com.minute_of_fame.queue.models

import com.minute_of_fame.queue.models.JsonPackets.Command
import io.circe.{Decoder, Encoder, Json}
import io.circe._
import io.circe.generic.JsonCodec
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import io.circe.generic.semiauto._
import io.circe.generic.extras._

import scala.util.{Failure, Success, Try}

object JsonPackets {

  implicit val config: Configuration =
    Configuration.default.withDiscriminator("type").withSnakeCaseMemberNames

  @ConfiguredJsonCodec case class CommandPacket(command: String, data: Command)

  @ConfiguredJsonCodec sealed trait Command
  @ConfiguredJsonCodec case class AddToQueue(streamType: String, id: String, title: String, description: String) extends Command
  case class StopStream() extends Command
  //Internal commands
  case class SetRtcStream(id: Int) extends Command

  case class SetTime(time: Int) extends Command
  case class UpdatePlace(queue: Array[Int]) extends Command
  case class SetStream(id: String, publisher: String, title: String, description: String) extends Command

  object CommandEncoder {
    implicit val encodeEvent: Encoder[Command] = Encoder.instance {_.asJson}
  }

  object CommandPacketDecoder {

    implicit val encodeCommandPacket: Encoder[CommandPacket] = (a: CommandPacket) => Json.obj(
      ("command", Json.fromString(a.command)),
      ("data", a.data.asJson.withObject(_.filter(_._1 != "type").asJson))
    ) //implicit def decodeImp[CommandPacket]: Decoder[CommandPacket] = Decoder.instanceTry(c => {
    implicit val decodeCommandPacket: Decoder[CommandPacket] = (c: HCursor) =>
      c.downField("command").as[String] match {
        case Right(v) =>
          (v match {
            case "queue" => c.get[AddToQueue]("data")
            case "stop" => c.get[StopStream]("data")
            case "set_time" => c.get[SetTime]("data")
            case "update_place" => c.get[UpdatePlace]("data")
            case "set_stream" => c.get[SetStream]("data")
          }) map (CommandPacket("", _))
        case Left(e) => Left(e)
      }
  }
}
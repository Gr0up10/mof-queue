package com.minute_of_fame.queue.actors

import akka.actor.{Actor, Props}
import com.minute_of_fame.queue.actors.DataBase.{GetUser, SaveStream, UserInfo}
import com.minute_of_fame.queue.models.DbModels._
import io.getquill.context.jdbc.{Decoders, Encoders}
import io.getquill.{Literal, PostgresJdbcContext}
import io.getquill._

object DataBase {
  def props = Props(classOf[DataBase])

  case class GetUser(id: Int)
  case class SaveStream(stream: AppStream)

  case class UserInfo(user: AuthUser)
}

object ctx extends PostgresJdbcContext(SnakeCase, "db") with Encoders with Decoders

class DataBase extends Actor {
  import ctx._

  override def receive: Receive = {
    case GetUser(id) => sender() ! UserInfo(run(query[AuthUser].filter(_.id == lift(id))).lift(0).orNull)
    case model: SaveStream => run(query[AppStream].insert(lift(model.stream)).returningGenerated(_.id))
  }
}

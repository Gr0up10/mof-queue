package com.minute_of_fame.queue.actors

import akka.actor.{Actor, ActorLogging, Props}
import com.minute_of_fame.queue.actors.DataBase.{GetCurrentLikeDislikeRatio, GetUser, SaveStream, UserInfo}
import com.minute_of_fame.queue.models.DbModels._
import io.getquill.context.jdbc.{Decoders, Encoders}
import io.getquill.{Literal, PostgresJdbcContext}
import io.getquill._

object DataBase {
  def props = Props(classOf[DataBase])

  case class GetUser(id: Int)
  case class SaveStream(stream: AppStream)
  case class GetCurrentLikeDislikeRatio()

  case class UserInfo(user: AuthUser)
}

object ctx extends PostgresJdbcContext(SnakeCase, "db") with Encoders with Decoders

class DataBase extends Actor with ActorLogging {
  import ctx._

  private var currentStreamId = -1

  override def receive: Receive = {
    case GetUser(id) => sender() ! UserInfo(run(query[AuthUser].filter(_.id == lift(id))).lift(0).orNull)

    case model: SaveStream =>
      log.info(s"Saving stream ${model.stream}")
      run(query[AppStream].filter(_.active).update(_.active -> lift(false)))
      currentStreamId = run(query[AppStream].insert(lift(model.stream)).returningGenerated(_.id))

    case GetCurrentLikeDislikeRatio() =>
      val ratio = run(query[AppPollstat].filter(_.streamId == lift(currentStreamId)))
                    .foldRight((0, 0))((model, ratio) => (ratio._1+1, ratio._2+model.vote))
      if(ratio._1 == 0) sender() ! 0.toDouble
      else sender() ! ratio._2.toDouble / ratio._1.toDouble
  }
}

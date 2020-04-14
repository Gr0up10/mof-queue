package com.minute_of_fame.queue.actors

import com.minute_of_fame.queue.models.DbModels.{AppStream, AuthUser}

object DataBase {


  case class GetUser(id: Int)
  case class SaveStream(stream: AppStream)

  case class UserInfo(user: AuthUser)
}


class DataBase {

}

package com.minute_of_fame.queue.models

import java.time.LocalDate

object DbModels {

  case class AppPollstat(id: Int, vote: Int, streamId: Int, userId: Int, date: LocalDate)

  case class AppStream(id: Int = 0, streamId: String = "", active: Boolean = true, publisherId: Int = 0,
                       date: LocalDate = LocalDate.now(), pending: Boolean = false, description: String = "",
                       title: String = "", views: Int = 0)

  case class AuthUser(id: Int = 0, password: String = "", lastLogin: LocalDate = LocalDate.now(),
                      isSuperuser: Boolean = false, username: String = "", firstName: String = "",
                      lastName: String = "", email: String = "", isStaff: Boolean = false, isActive: Boolean = true,
                      dateJoined: LocalDate = LocalDate.now())

}

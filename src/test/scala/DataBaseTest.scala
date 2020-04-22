import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.minute_of_fame.queue.actors.DataBase
import com.minute_of_fame.queue.models.DbModels
import com.minute_of_fame.queue.models.DbModels.{AppStream, AuthUser}
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike, Matchers}

//TODO: Come up with the way to generate test db

/*
class DataBaseTest()
  extends TestKit(ActorSystem("MySpec"))
    with ImplicitSender
    with Matchers
    with FunSuiteLike
    with BeforeAndAfterAll {
  import com.minute_of_fame.queue.actors.ctx._
  import com.minute_of_fame.queue.actors.ctx

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)

    ctx.run(ctx.quote {ctx.query[AppStream].delete})
    ctx.run(ctx.quote {ctx.query[AuthUser].delete})
  }

  override def beforeAll(): Unit = {
    ctx.run(ctx.quote {ctx.query[AppStream].delete})
    ctx.run(ctx.quote {ctx.query[AuthUser].delete})
    ctx.run(ctx.quote {ctx.query[AuthUser].insert(ctx.lift(AuthUser()))})
  }

  val db = system.actorOf(DataBase.props)

  test("Get user") {
    db ! DataBase.GetUser(0)
    expectMsg(DataBase.UserInfo(AuthUser()))
  }

  test("Save stream") {
    db ! DataBase.SaveStream(DbModels.AppStream())
    expectNoMessage()
    val res = ctx.run(ctx.query[DbModels.AppStream].filter(_.id == 0))
    assert(res.size == 1)
  }
}
*/
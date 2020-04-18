import java.net.InetSocketAddress

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.minute_of_fame.queue.Main.{actorSystem, db}
import com.minute_of_fame.queue.actors.{Client, DataBase, QueueHandler, QueueProtocol, Session}
import com.minute_of_fame.queue.models.DbModels.{AppStream, AuthUser}
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike, Matchers}

class IntegrationTest()
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

  var db = system.actorOf(DataBase.props)
  var handler = system.actorOf(QueueHandler.props(db), "queue_handler")
  val protocol = system.actorOf(QueueProtocol.props(db, handler), "protocol")

  protocol ! "connected"
  expectMsg(packets.Register("queue"))


}

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestActor, TestKit, TestProbe}
import com.minute_of_fame.queue.actors.QueueHandler.{Connected, Disconnected}
import com.minute_of_fame.queue.actors.{DataBase, QueueHandler, QueueProtocol}
import com.minute_of_fame.queue.models.{DbModels, JsonPackets}
import com.minute_of_fame.queue.models.JsonPackets.{AddToQueue, Command, CommandPacket, StopStream}
import com.minute_of_fame.queue.models.JsonPackets.CommandPacketDecoder._
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike, Matchers}
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._

class QueueProtocolTest()
  extends TestKit(ActorSystem("MySpec"))
    with ImplicitSender
    with Matchers
    with FunSuiteLike
    with BeforeAndAfterAll {

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  val queue = TestProbe()
  val db = TestProbe()
  db.setAutoPilot((sender: ActorRef, msg: Any) => msg match {
    case DataBase.GetUser(id) =>
      sender ! DataBase.UserInfo(DbModels.AuthUser(id=id))
      TestActor.KeepRunning
  })
  val poll = system.actorOf(QueueProtocol.props(db.ref, queue.ref))

  test("Queue register test") {
    poll ! "connected"
    expectMsg(packets.Register("queue"))
    queue.expectMsg("connected")
  }

  def packCommand(id: Int, name: String, cmd: Command) =
    packets.Packet(id, data=CommandPacket(name, if(cmd!=null) cmd else StopStream()).asJson.noSpaces)

  test("Json encoder") {
    print(AddToQueue("321", "123").asJson.noSpaces)
    val json = CommandPacket("queue", AddToQueue("321", "123")).asJson.noSpaces
    assert(json == "{\"command\":\"queue\",\"data\":{\"stream_type\":\"321\",\"id\":\"123\"}}")
  }

  test("Queue session packet handling") {
    poll ! packets.Packet(3, isAuth = true, data="connected")
    db.expectMsg(DataBase.GetUser(3))
    queue.expectMsg(Connected(3))

    poll ! packCommand(3, "queue", AddToQueue("", "123"))
    queue.expectMsg(QueueHandler.AddToQueue(3))

    poll ! packCommand(3, "stop", StopStream())
    queue.expectMsg(QueueHandler.StopStream(3))

    poll ! packCommand(3, "stop", StopStream())
    queue.expectNoMessage()

    poll ! packets.Packet(3, isAuth = true, data="disconnected")
    queue.expectMsg(Disconnected(3))
  }

  test("Queue handler packet processing") {
    poll ! packets.Packet(1, isAuth = true, data="connected")
    db.expectMsg(DataBase.GetUser(1))
    queue.expectMsg(Connected(1))
    poll ! packets.Packet(2, isAuth = true, data="connected")
    db.expectMsg(DataBase.GetUser(2))
    queue.expectMsg(Connected(2))
    poll ! packCommand(1, "queue", AddToQueue("", "123"))
    queue.expectMsg(QueueHandler.AddToQueue(1))

    poll ! QueueHandler.SetTime(10)
    expectMsg(packCommand(1, "set_time", JsonPackets.SetTime(10)))
    expectMsg(packCommand(2, "set_time", JsonPackets.SetTime(10)))

    poll ! QueueHandler.SetStream(1, 2)
    expectMsg(packCommand(2, "set_stream", JsonPackets.SetStream("123")))
    poll ! QueueHandler.SetStream(1, -1)
    //expectMsg(packCommand(1, "set_stream", JsonPackets.SetStream("123")))
    expectMsg(packCommand(2, "set_stream", JsonPackets.SetStream("123")))

    poll ! QueueHandler.UpdatePlaces(Array(1))
    expectMsg(packCommand(1, "update_places", JsonPackets.UpdatePlace(Array(1))))
    expectMsg(packCommand(2, "update_places", JsonPackets.UpdatePlace(Array(1))))

  }
}

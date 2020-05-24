import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestActor, TestActors, TestKit, TestProbe}
import com.minute_of_fame.queue.actors.{DataBase, QueueHandler}
import com.minute_of_fame.queue.models.DbModels
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike, Matchers}

class QueueHandlerTest()
  extends TestKit(ActorSystem("MySpec"))
    with ImplicitSender
    with Matchers
    with FunSuiteLike
    with BeforeAndAfterAll {

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  test("Queue handler test"){
    import QueueHandler._
    val db = TestProbe()
    db.setAutoPilot((sender: ActorRef, msg: Any) => msg match {
      case DataBase.GetCurrentLikeDislikeRatio() =>
        sender ! 0.toDouble
        TestActor.KeepRunning
    })
    val queue = system.actorOf(QueueHandler.props(db.ref, 2))
    queue ! "connected"
    queue ! Tick()
//    expectMsg(QueueHandler.UpdateViewers(0))
    queue ! AddToQueue(1)
    var msg = expectMsgClass(classOf[UpdatePlaces])
    assert(msg.queue.sameElements(Array(1)))
    expectMsg(SetStream(1))
    queue ! Tick()
    msg = expectMsgClass(classOf[UpdatePlaces])
    assert(msg.queue.isEmpty)
    expectMsg(SetTime(2))
//    expectMsg(QueueHandler.UpdateViewers(0))
    queue ! Connected(9)
    expectMsg(SetStream(1, 9))
    queue ! AddToQueue(2)
    msg = expectMsgClass(classOf[UpdatePlaces])
    assert(msg.queue.sameElements(Array(2)))
    queue ! AddToQueue(3)
    msg = expectMsgClass(classOf[UpdatePlaces])
    assert(msg.queue.sameElements(Array(2, 3)))
    queue ! Tick()
    expectMsg(SetTime(1))
    expectMsg(QueueHandler.UpdateViewers(1))
    queue ! Tick()
    expectMsg(SetTime(0))
    expectMsg(SetStream(2))
    msg = expectMsgClass(classOf[UpdatePlaces])
    assert(msg.queue.sameElements(Array(3)))
    //expectMsg(SetTime(2))
    //expectMsg(QueueHandler.UpdateViewers(1))
    queue ! StopStream(2)
    expectMsg(SetStream(3))
    msg = expectMsgClass(classOf[UpdatePlaces])
    assert(msg.queue.isEmpty)
    queue ! Tick()
    expectMsg(SetTime(2))
    //expectMsg(QueueHandler.UpdateViewers(1))
    queue ! Disconnected(3)
    queue ! AddToQueue(1)
    msg = expectMsgClass(classOf[UpdatePlaces])
    assert(msg.queue.sameElements(Array(1)))
  }
}
package kvstore

import akka.actor.{Actor, ActorRef, Cancellable, Props}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps

object Replicator {
  case class Replicate(key: String, valueOption: Option[String], id: Long)
  case class Replicated(key: String, id: Long)
  
  case class Snapshot(key: String, valueOption: Option[String], seq: Long)
  case class SnapshotAck(key: String, seq: Long)

  def props(replica: ActorRef): Props = Props(new Replicator(replica))
}

class Replicator(val replica: ActorRef) extends Actor {
  import Replicator._
  import Replica._
  import context.dispatcher
  
  /*
   * The contents of this actor is just a suggestion, you can implement it in any way you like.
   */

  // map from sequence number to pair of sender and request
  var acks = Map.empty[Long, (ActorRef, Replicate)]
  // a sequence of not-yet-sent snapshots (you can disregard this if not implementing batching)
  var pending = Vector.empty[Snapshot]
  
  var _seqCounter = 0L
  def nextSeq() = {
    val ret = _seqCounter
    _seqCounter += 1
    ret
  }

  var snapOps = Map.empty[(String, Long), (ActorRef, Cancellable, Long)]

  def receive: Receive = {
    case Replicate(key, valueOption, id) =>
      val seq = nextSeq()
      snap(key, valueOption, id, seq, sender)
    case SnapshotAck(key, seq) =>
      snapOps.get((key, seq)).foreach {
        case (snapSender, cancellable, id) =>
          snapSender ! Replicated(key, id)
          cancellable.cancel()
      }
      snapOps = snapOps.filterKeys {
        case (`key`,`seq`) => false
        case _ => true
      }
  }

  private def snap(key: String, valueOption: Option[String], id: Long, seq: Long, sender: ActorRef): Unit = {
    // Schedule persist every 100 millis
    val cancellable = context.system.scheduler
      .schedule(0 millis, 100 millis, replica, Snapshot(key, valueOption, seq))(implicitly[ExecutionContext], self)

    // Store as state
    snapOps.get((key, seq)).foreach(_._2.cancel())
    snapOps = snapOps.updated((key, seq), (sender, cancellable, id))

    // Cancel after 300 millis
    context.system.scheduler.scheduleOnce(1 second) {
      cancellable.cancel()
      snapOps = snapOps.filterKeys {
        case (`key`,`seq`) => false
        case _ => true
      }
    }
  }


}

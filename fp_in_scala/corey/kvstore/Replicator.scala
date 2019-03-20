package kvstore

import akka.actor.{Actor, ActorRef, Props, Stash}

import scala.concurrent.duration._
import scala.concurrent.duration._

object Replicator {
  case class Replicate(key: String, valueOption: Option[String], id: Long)
  case class Replicated(key: String, id: Long)
  
  case class Snapshot(key: String, valueOption: Option[String], seq: Long)
  case class SnapshotAck(key: String, seq: Long)

  def props(replica: ActorRef): Props = Props(new Replicator(replica))
}

class Replicator(val replica: ActorRef) extends Actor with Stash{
  import Replicator._
  import Replica._
  import context.dispatcher
  
  /*
   * The contents of this actor is just a suggestion, you can implement it in any way you like.
   */
  case class LeaderReplicate(leader: ActorRef, replicate: Replicate)

  // map from sequence number to pair of sender and request
  var acks = Map.empty[Long, LeaderReplicate]
  // a sequence of not-yet-sent snapshots (you can disregard this if not implementing batching)
  var pending = Vector.empty[Snapshot]
  
  var _seqCounter = 0L
  def nextSeq() = {
    val ret = _seqCounter
    _seqCounter += 1
    ret
  }
  
  /* TODO Behavior for the Replicator. */
  def receive: Receive = {

    case x @ Replicate(k, vo, id) =>
      val seq = nextSeq()
      acks += (seq -> LeaderReplicate(sender(), x))
      val cancelable = context.system.scheduler.schedule(0 millis, 100 millis, replica, Snapshot(k, vo, seq))
      context.become({
        case SnapshotAck(k, s) if s == seq =>
          cancelable.cancel()
          val x = acks(s)
          x.leader ! Replicated(x.replicate.key, x.replicate.id)
          acks -= s
          unstashAll()
          context.unbecome()

        case y: Replicate =>
          stash()

        case _ =>
      })

    case _ =>
  }

}

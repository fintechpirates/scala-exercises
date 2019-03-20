package kvstore

import java.time.Instant

import akka.actor.{Actor, ActorRef, Cancellable, OneForOneStrategy, PoisonPill, Props, SupervisorStrategy, Terminated}
import kvstore.Arbiter._

import scala.collection.immutable.Queue
import akka.actor.SupervisorStrategy.Restart

import scala.annotation.tailrec
import akka.pattern.{ask, pipe}

import scala.concurrent.duration._
import akka.util.Timeout

object Replica {
  sealed trait Operation {
    def key: String
    def id: Long
  }
  case class Insert(key: String, value: String, id: Long) extends Operation
  case class Remove(key: String, id: Long) extends Operation
  case class Get(key: String, id: Long) extends Operation

  sealed trait OperationReply
  case class OperationAck(id: Long) extends OperationReply
  case class OperationFailed(id: Long) extends OperationReply
  case class GetResult(key: String, valueOption: Option[String], id: Long) extends OperationReply

  def props(arbiter: ActorRef, persistenceProps: Props): Props = Props(new Replica(arbiter, persistenceProps))
}

class Replica(val arbiter: ActorRef, persistenceProps: Props) extends Actor {
  import Replica._
  import Replicator._
  import Persistence._
  import context.dispatcher

  /*
   * The contents of this actor is just a suggestion, you can implement it in any way you like.
   */
  
  var kv = Map.empty[String, String]
  // a map from secondary replicas to replicators
  var secondaries = Map.empty[ActorRef, ActorRef]
  // the current set of replicators
  var replicators = Set.empty[ActorRef]

  val persistence = context.actorOf(persistenceProps)

  case class PersistRetry(replicator: ActorRef, cancel: Cancellable, op: Any, created: Instant = Instant.now())
  var persisted = Map.empty[Long, PersistRetry]
  var replicatorConfirmation = Map.empty[Long, Set[ActorRef]]


  arbiter ! Arbiter.Join

  def receive = {
    case JoinedPrimary   =>
      // reaper ...
      context.system.scheduler.schedule(1 second, 1 second){
        val now = Instant.now()
        persisted = persisted.filter{
          case (k, v) =>
            if( Math.abs(java.time.Duration.between(now, v.created).toMillis) > 1000 ){
              v.cancel.cancel()
              //println("REAPER !!!")
              v.replicator ! OperationFailed(k)
              false
            }else true
        }
      }
      context.become(leader)
    case JoinedSecondary => context.become(replica)
  }

  def checkConsistancy(id: Long, ref: ActorRef) = {
    replicatorConfirmation.get(id).foreach { noResponseSet =>
      //println(s"size: ${replicatorConfirmation(id).size}")
      replicatorConfirmation += id -> (noResponseSet - ref)
      if (replicatorConfirmation(id).isEmpty) {
        //println("SEND ACK")
        val retry = persisted(id)
        retry.op match {
          case Insert(key, value, id) => kv += key -> value
          case Remove(key, id) => kv -= key
        }
        retry.cancel.cancel()
        retry.replicator ! OperationAck(id)
        persisted -= id
        replicatorConfirmation -= id
      }
    }
  }


  /* TODO Behavior for  the leader role. */
  val leader: Receive = {
    case x @ Insert(key, value, id) =>
      //kv += key -> value
      //context.sender() ! OperationAck(id)
      val client = sender()
      persisted += id -> PersistRetry(client, context.system.scheduler.schedule(0 millis, 100 millis, persistence, Persist(key, Some(value), id)), x)
      replicatorConfirmation += id -> (replicators + self)
      replicators.foreach(_ ! Replicate(key, Some(value), id))

    case x @ Remove(key, id) =>
      // kv -= key
      //context.sender() ! OperationAck(id)
      val client = sender()
      persisted += id -> PersistRetry(client, context.system.scheduler.schedule(0 millis, 100 millis, persistence, Persist(key, None, id)), x)
      replicatorConfirmation += id -> (replicators + self)
      replicators.foreach(_ ! Replicate(key, None, id))

    case Get(key, id) =>
      context.sender() ! GetResult(key, kv.get(key), id)

    case Replicated(key, id) =>
      checkConsistancy(id, sender())

    case Persisted(k, id) =>
      val retry = persisted(id)
      retry.cancel.cancel()
      checkConsistancy(id, self)

    case Replicas(r: Set[ActorRef]) =>
      val replicas = r.filterNot(_ == self)
      val toAdd = replicas -- secondaries.keySet
      val roRem = secondaries.keySet -- replicas
      toAdd.foreach{ a =>
        val rep = context.actorOf(Props(new Replicator(a)))
        secondaries += a -> rep
        replicators += rep
        kv.foreach{
          case (k, v) => rep ! Replicate(k, Some(v), 1)
        }
      }
      roRem.foreach{ a =>
        val replicator = secondaries(a)
        replicatorConfirmation.foreach{
          case (k, xs) =>
            checkConsistancy(k, replicator)
        }
        replicator ! PoisonPill
        replicators -= secondaries(a)
        secondaries -= a
      }

    case _ =>
  }

  var seq = -1L


  /* TODO Behavior for the replica role. */
  val replica: Receive = {
    case Get(key, id) =>
      context.sender() ! GetResult(key, kv.get(key), id)

    case Snapshot(k, vo, s) if s <= seq =>
      sender() ! SnapshotAck(k, s)

    case Snapshot(k, vo, s) if s > (seq+1) =>

    case x @ Snapshot(k, vo, s) =>
      val replicator = sender()
      vo match{
        case Some(v) => kv += k -> v
        case None => kv -= k
      }
      persisted += s -> PersistRetry(replicator, context.system.scheduler.schedule(0 millis, 100 millis, persistence, Persist(k, vo, s)), x)

    case Persisted(k, id) =>
      persisted.get(id).foreach { retry =>
        seq = id
        retry.cancel.cancel()
        retry.replicator ! SnapshotAck(k, id)
        persisted -= id

      }

    case _ =>
  }

}


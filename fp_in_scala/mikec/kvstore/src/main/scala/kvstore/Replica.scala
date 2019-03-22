package kvstore

import akka.actor.{Actor, ActorRef, Cancellable, OneForOneStrategy, PoisonPill, Props, SupervisorStrategy, Terminated}

import kvstore.Arbiter._
import scala.collection.immutable.Queue
import akka.actor.SupervisorStrategy.Restart
import scala.annotation.tailrec
import akka.pattern.{ask, pipe}
import scala.concurrent.duration._
import akka.util.Timeout
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

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

  implicit val timeout = Timeout(1 second)
  /*
   * The contents of this actor is just a suggestion, you can implement it in any way you like.
   */

  var acks = Map.empty[(String, Long), (ActorRef, Set[ActorRef])]
  var persistOps = Map.empty[(String, Long), (ActorRef, Cancellable, Option[String])]
  var opTimeouts = Map.empty[(String, Long), Cancellable]

  var kv = Map.empty[String, String]
  // a map from secondary replicas to replicators
  var secondaries = Map.empty[ActorRef, ActorRef]
  // the current set of replicators
  var replicators = Set.empty[ActorRef]

  val persistence = context.actorOf(persistenceProps)

  arbiter ! Join

  def receive = {
    case JoinedPrimary   => context.become(leader)
    case JoinedSecondary => context.become(replica)
  }

  /** PRIMARY FROM HERE */
  val leader: Receive = {
    case Insert(key, value, id) =>
      kv = kv.updated(key, value)
      persist(key, Some(value), id, sender)
      enforceTimeout(key, id, sender, failure = Some(OperationFailed(id)))
    case Remove(key, id) =>
      kv = kv.filterKeys(_ != key)
      persist(key, None, id, sender)
      enforceTimeout(key, id, sender, failure = Some(OperationFailed(id)))

    case Get(key, id) =>
      sender ! GetResult(key, kv.get(key), id)

    case Replicas(replicas) =>
      updateReplicas(replicas)

    case Persisted(key, id) =>
      persistOps.get((key, id)).foreach {
        case (persistSender, _, valueOpt) =>
          replicate(key, id, persistSender, valueOpt)
      }
      clearPersist(key, id)

    case Replicated(key: String, id: Long) =>
      acks.get((key, id)).foreach { case (opsSender, currentAcks) =>
        val updatedAcks = currentAcks + sender
        if((replicators diff updatedAcks).isEmpty) {
          opsSender ! OperationAck(id)
          clearOps(key, id)
        } else {
          acks = acks.updated((key, id), (opsSender, updatedAcks))
        }
      }
  }

  private def replicate(
    key: String,
    id: Long,
    opsSender: ActorRef,
    valueOpt: Option[String]
  ): Unit = {
    updateExpectedSeq(id)
    acks = acks.updated((key, id), (opsSender, Set.empty[ActorRef]))
    if(replicators.isEmpty) {
      opsSender ! OperationAck(id)
      clearOps(key, id)
    } else {
      replicators.foreach(_ ! Replicate(key, valueOpt, id))
    }
  }

  private def enforceTimeout(key: String, id: Long, opSender: ActorRef, failure: Option[Any] = None): Unit = {
    val opTimeout = context.system.scheduler.scheduleOnce(1 seconds) {
      failure.foreach(opSender ! _)
      clearOps(key, id)
    }

    opTimeouts.get((key, id)).foreach(_.cancel())
    opTimeouts = opTimeouts.updated((key, id), opTimeout)
  }

  private def clearPersist(key: String, id: Long): Unit  = {
    persistOps.get((key, id)).foreach(_._2.cancel())
    persistOps = persistOps.filterKeys {
      case (`key`,`id`) => false
      case _ => true
    }
  }

  private def clearTimeout(key: String, id: Long): Unit  = {
    opTimeouts.get((key, id)).foreach(_.cancel())
    opTimeouts = opTimeouts.filterKeys {
      case (`key`,`id`) => false
      case _ => true
    }
  }

  private def clearOps(key: String, id: Long): Unit  = {
    clearPersist(key, id)
    clearTimeout(key, id)
    acks = acks.filterKeys {
      case (`key`,`id`) => false
      case _ => true
    }
  }

  private def updateReplicas(replicas: Set[ActorRef]): Unit = {
    // get departing replicas
    val departingReplicas = secondaries.keySet diff (replicas - self)
    // get new replicas
    val newReplicas = (replicas - self) diff secondaries.keySet

    // stop replicators for departing replicas
    secondaries.filterKeys(departingReplicas.contains).values.foreach(context.stop)

    // create new replicators for departing replicas, replicate all current KV
    val withReplicators = newReplicas.map { replica =>
      val replicator = context.actorOf(Replicator.props(replica))
      // Replicate ongoing acks --
      acks.keys.foreach {
        case (key, id) =>
          replicator ! Replicate(key, kv.get(key), id)
      }
      // Replicate completed acks --
      val replicating = acks.keys.map(_._1).toSet
      (kv --  replicating).foreach{
        case (key, value) =>
          replicator ! Replicate(key, Some(value), IdGen.getId)
      }
      replica -> replicator
    }

    // update state
    secondaries = secondaries ++ withReplicators -- departingReplicas
    replicators = secondaries.values.toSet

    // Ack outstanding operations that have completed replication with current replicators
    acks = acks.filter {
      case ((_, id), (opsSender, currentAcks)) if (replicators diff currentAcks).isEmpty =>
        opsSender ! OperationAck(id)
        false
      case _ =>
        true
    }
  }

  /** REPLICA FROM HERE **/
  private var expectedSeq: Long = 0L
  val replica: Receive = {
    case Snapshot(key, valueOption, seq) =>
      processSnapshot(key, valueOption, seq, sender)
    case Get(key, id) =>
      sender ! GetResult(key, kv.get(key), id)

    case Persisted(key, id) =>
      persistOps.get((key, id)).foreach {
        case (persistSender, persistRetry, _) =>
          updateExpectedSeq(id)
          persistSender ! SnapshotAck(key, id)
          persistRetry.cancel()
      }
      persistOps = persistOps.filterKeys {
        case (`key`,`id`) => false
        case _ => true
      }
  }

  private def processSnapshot(key: String, valueOption: Option[String], seq: Long, sender: ActorRef): Unit = {
    valueOption match {
      case Some(value) if seq == expectedSeq =>
        kv = kv.updated(key, value)
        persist(key, valueOption, seq, sender)
        enforceTimeout(key, seq, sender)
      case None if seq == expectedSeq =>
        kv = kv.filterKeys(_ != key)
        persist(key, valueOption, seq, sender)
        enforceTimeout(key, seq, sender)
      case _ if seq < expectedSeq =>
        updateExpectedSeq(seq)
        sender ! SnapshotAck(key, seq)
      case _ if seq > expectedSeq =>
        ()
    }
  }

  private def updateExpectedSeq(seq: Long): Unit =
    expectedSeq = math.max(expectedSeq, seq + 1)

  private def persist(
    key: String,
    valueOption: Option[String],
    id: Long,
    persistSender: ActorRef
  ): Unit = {
    // Schedule persist every 100 millis
    val persistRetry = context.system.scheduler
      .schedule(0 millis, 100 millis, persistence, Persist(key, valueOption, id))(implicitly[ExecutionContext], self)

    // Store as state
    persistOps.get((key, id)).foreach(_._2.cancel())
    persistOps = persistOps.updated((key, id), (persistSender, persistRetry, valueOption))
  }
}

object IdGen {
  private var state: Long = Long.MaxValue

  def getId: Long = {
    val id = state
    state -= 1
    id
  }
}
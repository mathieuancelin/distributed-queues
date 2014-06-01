package queue

import akka.actor._
import scala.concurrent.{Future, Await}
import akka.pattern.ask
import java.io.File
import tools.{FileUtils, IdGenerator, Constants}
import java.util.concurrent.atomic.{AtomicLong, AtomicInteger}
import collection.JavaConversions._
import akka.cluster.{Member, Cluster}
import akka.cluster.ClusterEvent._
import akka.cluster.ClusterEvent.MemberUp
import akka.cluster.ClusterEvent.UnreachableMember
import java.util.Collections
import play.api.libs.json.JsObject
import scala.util.Random

class ActorQueue(val name: String, val diskWriter: ActorRef) extends Actor {

  val queue: FileBackedQueue = new FileBackedQueue(name, diskWriter)
  val counter = new AtomicInteger(0)

  def append(blob: JsObject, sender: ActorRef) = {
    val id = queue.append(blob)
    sender ! Added(id)
  }

  def poll(sender: ActorRef) = {
    sender ! Blob(queue.poll())
    if (Constants.compressEvery != -1 && counter.compareAndSet(Constants.compressEvery, 0)) {
      // TODO : avoid blocking here, waste of time ...
      val start = System.currentTimeMillis()
      val path = Await.result(ask(diskWriter, SendFilePath())(Constants.timeout).mapTo[FilePath].map(_.path)(context.system.dispatcher), Constants.timeout.duration)
      FileUtils.emptyFile(new File(path))
      queue.queue.foreach(line => FileUtils.appendOffer(new File(path), queue.name, IdGenerator.nextId(), line))
      Constants.logger.info(s"File compression in ${System.currentTimeMillis() - start} ms")
    } else counter.incrementAndGet()
  }

  def receive: Receive = {
    case mess @ Append(_, blob) => append(blob, sender())
    case mess @ Poll(_) => poll(sender())
    case mess @ Size(_) => sender() ! QueueSize(queue.size())
    case mess @ Clear(_) => {
      queue.clear()
      sender() ! Cleared()
    }
    case ReplayAppend(_, blob) => queue.append(blob, false)
    case ReplayPoll(_) => queue.poll(false)
    case ReplicationAppend(_, blob) => append(blob, sender())
    case ReplicationPoll(_) => poll(sender())
    case _ =>
  }
}

class MasterActor extends Actor {
  implicit val timeout = Constants.bigTimeout

  def receive: Receive = {
    case mess @ Append(name, blob) => QueuesManager.routeToQueue(name, sender(), mess)
    case mess @ Poll(name) => QueuesManager.routeToQueue(name, sender(), mess)
    case mess @ Size(name) => {
      implicit val ec = context.system.dispatcher
      val to = sender()
      Future.sequence(QueuesClusterState.refs(s"queue-$name").map(queue => (queue ? mess).mapTo[QueueSize].map(_.size)))
        .map(listOfSizes => listOfSizes.sum).map(sum => to ! QueueSize(sum))
    }
    case mess @ Clear(name) => {
      implicit val ec = context.system.dispatcher
      val to = sender()
      Future.sequence(QueuesClusterState.refs(s"queue-$name").map(queue => (queue ? mess).mapTo[Cleared])).map { _ =>
        to ! Cleared()
      }
    }
    case CreateQueue(name) => {
      QueuesManager.createQueue(name, true)
      sender() ! QueueCreated()
    }
    case DeleteQueue(name) => {
      QueuesManager.deleteQueue(name, true)
      sender() ! QueueDeleted()
    }
    case ReplicationCreateQueue(name) => {
      QueuesManager.createQueue(name, false)
    }
    case ReplicationDeleteQueue(name) => {
      QueuesManager.deleteQueue(name, false)
    }
    case _ =>
  }
}

class FileWriter(name: String, root: File) extends Actor {

  val log = new File(root, s"$name-queue.log")
  if (!log.exists()) {
    log.createNewFile()
  }

  def receive: Actor.Receive = {
    case AppendToLog(id, _, blob) => FileUtils.appendOffer(log, name, id, blob)
    case DeleteFromLog(_) => FileUtils.appendPoll(log, name)
    case ClearLog(_) => FileUtils.emptyFile(log)
    case SendFilePath() => sender() ! FilePath(log.getAbsolutePath)
    case _ =>
  }
}

class ClusterHandler extends Actor {

  val cluster = Cluster(context.system)

  override def preStart(): Unit = {
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent], classOf[UnreachableMember])
  }

  override def postStop(): Unit = cluster.unsubscribe(self)

  def receive = {
    case MemberUp(member) => {
      QueuesClusterState.addMember(member)
      QueuesClusterState.displayState()
    }
    case UnreachableMember(member) => {
      QueuesClusterState.removeMember(member)
      QueuesClusterState.displayState()
    }
    case MemberRemoved(member, previousStatus) => {
      QueuesClusterState.removeMember(member)
      QueuesClusterState.displayState()
    }
    case _: MemberEvent =>
  }
}

object QueuesClusterState {

  private[this] val next = new AtomicLong(0)
  private[this] val membersList = Collections.checkedList(new java.util.ArrayList[Member](), classOf[Member])

  def nextItem() = {
    if (Constants.roundRobin) {
      (next.getAndIncrement % membersList.size).toInt
    } else {
      Random.nextInt(membersList.size)
    }
  }

  def addMember(member: Member) = {
    if (!membersList.contains(member)) membersList.add(member)
  }
  def removeMember(member: Member) = if (membersList.contains(member)) membersList.remove(member)
  def members(): List[Member] = membersList.toList
  def membersWithoutMe(): List[Member] = {
    members().filter(member => member.address != QueuesManager.cluster().selfAddress)
  }
  def refs(to: String): List[ActorSelection] = membersList.toList.map(member => QueuesManager.system().actorSelection(ActorPath.fromString(member.address.toString) / "user" / to))
  def refsWithoutMe(to: String): List[ActorSelection] = {
    membersList.toList
      .filter(member => member.address != QueuesManager.cluster().selfAddress)
      .map(member => QueuesManager.system().actorSelection(ActorPath.fromString(member.address.toString) / "user" / to))
  }

  def selectNextMember(): Member = {
    if (membersList.isEmpty) throw new RuntimeException("No members ...")
    else membersList.get(nextItem())
  }

  def selectNextMemberAsRef(to: String): ActorSelection = {
    if (membersList.isEmpty) throw new RuntimeException("No members ...")
    else QueuesManager.system().actorSelection(ActorPath.fromString(membersList.get(nextItem()).address.toString) / "user" / to)
  }

  def displayState() = {
    Constants.logger.info(s"----------------------------------------------------------------------------")
    Constants.logger.info(s"Cluster members are : ")
    members().foreach { member =>
      Constants.logger.info(s"==> ${member.toString()}")
    }
    Constants.logger.info(s"----------------------------------------------------------------------------")
  }
}


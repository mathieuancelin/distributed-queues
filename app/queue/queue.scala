package queue

import java.util.concurrent.ConcurrentLinkedQueue
import play.api.libs.json.Json
import tools.{FileUtils, Reference, Constants, IdGenerator}
import akka.actor._
import scala.concurrent.Future
import play.api.libs.json.JsObject
import akka.pattern.ask
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import akka.cluster.Cluster

object QueuesManager {

  implicit val timeout = Constants.bigTimeout

  val system = Reference[ActorSystem]("system")
  val master = Reference[ActorRef]("master")
  val cluster = Reference[Cluster]("cluster")

  // highly blocking method to consolidate state before running app
  def onStart(as: ActorSystem, c: Cluster, m: ActorRef) = {
    master.set(m)
    system.set(as)
    cluster.set(c)
    if (!Constants.root.exists()) Constants.root.mkdirs()
    Constants.logger.info(s"Re-synchronization ...")
    val done = Constants.root.listFiles()
      .filter(file => file.getName.endsWith("-queue.log"))
      .map(file => {
      val queuename = file.getName.replace("-queue.log", "")
      Constants.logger.info(s"Re-synchronization of queue '$queuename'")
      val ref = createQueue(queuename, false)
      Constants.logger.info(s"Insertion of existing slugs in queue '$queuename'")
      val processed = FileUtils.readLines(file, (name, id, blob) => ref ! ReplayAppend(name, Json.parse(blob).as[JsObject]), (name) => ref ! ReplayPoll(name))
      Constants.logger.info(s"Inserted $processed items")
      // TODO : compress the queue
    })
    Constants.logger.info(s"Re-synchronization done (${done.size} queues.) !")
  }

  def routeToQueue(name: String, sender: ActorRef, command: QueueCommand): Future[Unit] = {
    // TODO : handle auto queue creation
    if (Constants.clusterRouting) {
      val fu = (QueuesClusterState.selectNextMemberAsRef(s"queue-$name") ? command).mapTo[Response].map { response =>
        sender ! response
      }
      if (Constants.fullReplication) {
        command match {
          case Append(_, blob) => QueuesClusterState.refsWithoutMe(s"queue-$name").foreach(ref => ref ! ReplicationAppend(name, blob))
          case Poll(_) => QueuesClusterState.refsWithoutMe(s"queue-$name").foreach(ref => ref ! ReplicationPoll(name))
        }
      }
      fu
    } else {
      (system().actorSelection(system() / s"queue-$name") ? command).mapTo[Response].map { response =>
        sender ! response
      }
    }
  }

  def createQueue(name: String, propagate: Boolean): ActorRef = {
    // TODO : check if queue exists
    val queueName = s"queue-$name"
    val writerName = s"queue-$name-writer"
    val writer = system().actorOf(Props(classOf[FileWriter], name, Constants.root), writerName)
    val queue = system().actorOf(Props(classOf[ActorQueue], name, writer), queueName)
    Constants.logger.info(s"Queue '$name' created ...")
    if (propagate) {
      QueuesClusterState.refsWithoutMe(Constants.masterName).foreach { ref =>
        ref ! ReplicationCreateQueue(name)
      }
    }
    queue
  }

  def deleteQueue(name: String, propagate: Boolean): Future[Unit] = {
    // TODO : delete logs
    val fu = for {
      _ <- system().actorSelection(system() / s"queue-$name") ? PoisonPill
      _ <- system().actorSelection(system() / s"queue-$name-writer") ? PoisonPill
    } yield ()
    if (propagate) {
      QueuesClusterState.refsWithoutMe(Constants.masterName).foreach { ref =>
        ref ! ReplicationDeleteQueue(name)
      }
    }
    fu
  }
}

private[queue] class FileBackedQueue(val name: String, val diskWriter: ActorRef) {

  val queue = new ConcurrentLinkedQueue[String]()

  def append(blob: JsObject, sync: Boolean = true): Long = {
    // TODO FEATURE : handle conflation
    val id = IdGenerator.nextId()
    val finalBlob = Json.stringify(blob ++ Json.obj("__queueStamp" -> id))
    if (sync) diskWriter ! AppendToLog(id, name, finalBlob)
    queue.offer(finalBlob)
    id
  }

  def poll(sync: Boolean = true): Option[JsObject] = {
    if (sync) diskWriter ! DeleteFromLog(name)
    val blob = queue.poll()
    val r = Option(blob).map(b => Json.parse(b).as[JsObject])
    r
  }

  def size(): Int = queue.size()

  def clear(sync: Boolean = true) = {
    if (sync) diskWriter ! ClearLog(name)
    queue.clear()
  }
}
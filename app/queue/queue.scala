package queue

import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue}
import play.api.libs.json.Json
import tools.{FileUtils, Reference, Constants, IdGenerator}
import akka.actor._
import scala.concurrent.Future
import play.api.libs.json.JsObject
import akka.pattern.ask
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import akka.cluster.Cluster
import java.util

object QueuesManager {

  implicit val timeout = Constants.bigTimeout

  val system = Reference.empty[ActorSystem]("system")
  val master = Reference.empty[ActorRef]("master")
  val cluster = Reference.empty[Cluster]("cluster")
  val existing = new util.HashSet[String]()

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
      ref ! CompressQueue()
    })
    Constants.logger.info(s"Re-synchronization done (${done.size} queues.) !")
  }

  def routeToQueue(name: String, sender: ActorRef, command: QueueCommand): Future[Unit] = {
    val context = MetricsStats.routingTime().time()
    if (Constants.autoCreateQueues && !existing.contains(name)) {
      createQueue(name, true)
    }
    if (Constants.clusterRouting) {
      val routee = QueuesClusterState.selectNextMemberAsRef(s"queue-$name")
      context.close()
      val fu = (routee ? command).mapTo[Response].map { response =>
        sender ! response
      }
      // TODO : uncomment when viable
      // if (Constants.fullReplication) {
      //   command match {
      //     case Append(_, blob) => QueuesClusterState.refsWithoutMe(s"queue-$name").foreach(ref => ref ! ReplicationAppend(name, blob))
      //     case Poll(_) => QueuesClusterState.refsWithoutMe(s"queue-$name").foreach(ref => ref ! ReplicationPoll(name))
      //   }
      // }
      fu
    } else {
      context.close()
      (system().actorSelection(system() / s"queue-$name") ? command).mapTo[Response].map { response =>
        sender ! response
      }
    }
  }

  def createQueue(name: String, propagate: Boolean): ActorSelection = {
    val queueName = s"queue-$name"
    val writerName = s"queue-$name-writer"
    if (!existing.contains(name)) {
      val writer = system().actorOf(Props(classOf[FileWriter], name, Constants.root), writerName)
      system().actorOf(Props(classOf[ActorQueue], name, writer), queueName)
      existing.add(name)
      Constants.logger.info(s"Queue '$name' created ...")
      if (propagate) {
        QueuesClusterState.refsWithoutMe(Constants.masterName).foreach { ref =>
          ref ! ReplicationCreateQueue(name)
        }
      }
    }
    system().actorSelection(s"/user/$queueName")
  }

  def deleteQueue(name: String, propagate: Boolean): Future[Unit] = {
    system().actorSelection(system() / s"queue-$name-writer") ! DeleteFile()
    existing.remove(name)
    FileBackedQueue.queues.remove(name)
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

private[queue] object FileBackedQueue {
  val queues = new ConcurrentHashMap[String, FileBackedQueue]()
}

private[queue] class FileBackedQueue(val name: String, val diskWriter: ActorRef) {

  FileBackedQueue.queues.putIfAbsent(name, this)  // Awful

  val queue = new ConcurrentLinkedQueue[String]()

  def append(blob: JsObject, sync: Boolean = true): Long = {
    val id = IdGenerator.nextId()
    val finalBlob = Json.stringify(blob ++ Json.obj("__queueStamp" -> id))
    if (sync) diskWriter ! AppendToLog(id, name, finalBlob)
    queue.offer(finalBlob)
    MetricsStats.queuesHits().mark()
    MetricsStats.queuesWriteHits().mark()
    id
  }

  def poll(sync: Boolean = true): Option[JsObject] = {
    if (sync) diskWriter ! DeleteFromLog(name)
    val blob = queue.poll()
    val r = Option(blob).map(b => Json.parse(b).as[JsObject])
    MetricsStats.queuesReadHits().mark()
    MetricsStats.queuesHits().mark()
    r
  }

  def size(): Int = {
    MetricsStats.queuesHits().mark()
    MetricsStats.queuesReadHits().mark()
    queue.size()
  }

  def clear(sync: Boolean = true) = {
    if (sync) diskWriter ! ClearLog(name)
    MetricsStats.queuesHits().mark()
    MetricsStats.queuesWriteHits().mark()
    queue.clear()
  }
}
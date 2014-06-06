import akka.actor.{ActorSystem, Props}
import akka.cluster.Cluster
import play.api.{Play, Application, GlobalSettings}
import queue.{MetricsStats, ClusterHandler, MasterActor, QueuesManager}
import tools.{Constants, Reference}

object Global extends GlobalSettings {

  val actorSystem = Reference.empty[ActorSystem]("queues-system")

  override def onStart(app: Application): Unit = {
    MetricsStats.onStart()
    actorSystem.set(ActorSystem(Constants.systemName, Play.current.configuration.underlying.getConfig("distributed-queues")))
    val master = actorSystem().actorOf(Props[MasterActor], Constants.masterName)
    val cluster = Cluster(actorSystem())
    QueuesManager.onStart(actorSystem(), cluster, master)
    actorSystem().actorOf(Props[ClusterHandler], Constants.clusterHandlerName)
  }

  override def onStop(app: Application): Unit = {
    MetricsStats.onStop()
    actorSystem.foreach(_.shutdown())
  }
}

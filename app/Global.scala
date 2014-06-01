import akka.actor.{ActorSystem, Props}
import akka.cluster.Cluster
import play.api.{Application, GlobalSettings}
import queue.{ClusterHandler, MasterActor, QueuesManager}
import tools.{Constants, Reference}

// TODO : metrics and stats page for the whole cluster
object Global extends GlobalSettings {

  val actorSystem = Reference[ActorSystem]("queues-system")

  override def onStart(app: Application): Unit = {
    actorSystem.set(ActorSystem(Constants.systemName))
    val master = actorSystem().actorOf(Props[MasterActor], Constants.masterName)
    val cluster = Cluster(actorSystem())
    QueuesManager.onStart(actorSystem(), cluster, master)
    actorSystem().actorOf(Props[ClusterHandler], Constants.clusterHandlerName)
  }

  override def onStop(app: Application): Unit = {
    actorSystem.foreach(_.shutdown())
  }
}

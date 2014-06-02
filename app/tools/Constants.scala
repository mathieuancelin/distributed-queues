package tools

import play.api.{Play, Logger}
import akka.util.Timeout
import java.util.concurrent.TimeUnit

object Constants {


  val timeout = Timeout(2, TimeUnit.SECONDS)
  val bigTimeout = Timeout(30, TimeUnit.SECONDS)
  val logger = Logger("DistributedQueues")
  val root = Play.current.getFile(Play.current.configuration.getString("distributed-queues.file-path").getOrElse("queues"))
  val generatorId = Play.current.configuration.getLong("distributed-queues.node-id").getOrElse(1L)
  val password = Play.current.configuration.getString("application.monitor-password").getOrElse(throw new RuntimeException("No password configured"))
  val token = Play.current.configuration.getString("application.api-token").getOrElse(throw new RuntimeException("No token configured"))
  val adminToken = Play.current.configuration.getString("application.api-admin-token").getOrElse(throw new RuntimeException("No token configured"))
  val clusterRouting = Play.current.configuration.getBoolean("distributed-queues.cluster-routing").getOrElse(true)
  val fullReplication = Play.current.configuration.getBoolean("distributed-queues.full-replication").getOrElse(false)
  val conflation = Play.current.configuration.getBoolean("distributed-queues.conflation").getOrElse(false)
  val roundRobin = Play.current.configuration.getBoolean("distributed-queues.round-robin-balancer").getOrElse(true)
  val compressEvery = Play.current.configuration.getInt("distributed-queues.compress-every").getOrElse(-1)
  val autoCreateQueues = Play.current.configuration.getBoolean("distributed-queues.auto-create-queues").getOrElse(false)
  val persistToDisk = Play.current.configuration.getBoolean("distributed-queues.persist-to-disk").getOrElse(true)
  val systemName = "queues-system"
  val masterName = "master-of-puppets"
  val clusterHandlerName = "cluster-handler"
}

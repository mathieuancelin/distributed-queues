name := """distributed-queues"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.1"

libraryDependencies ++= Seq(
  cache,
  ws,
  "com.typesafe.akka" %% "akka-cluster" % "2.3.3",
  //"net.openhft" % "collections" % "3.1.0",
  //"net.openhft" % "chronicle" % "3.1.1",
  "com.codahale.metrics" % "metrics-core" % "3.0.2"
)

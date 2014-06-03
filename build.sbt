name := """distributed-queues"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.1"

libraryDependencies ++= Seq(
  cache,
  ws,
  "com.typesafe.akka" %% "akka-cluster" % "2.3.3",
  "com.codahale.metrics" % "metrics-core" % "3.0.2",
  "com.codahale.metrics" % "metrics-json" % "3.0.2"
)

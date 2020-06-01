name := "akka-quickstart-scala"

version := "1.0"

scalaVersion := "2.13.1"

lazy val akkaVersion = "2.6.5"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
  "org.scalatest" %% "scalatest" % "3.1.0" % Test,
  "org.scalamock" %% "scalamock" % "4.4.0" % Test,
  "com.typesafe.play" %% "play-json" % "2.8.1",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2"
)

resolvers += "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/"

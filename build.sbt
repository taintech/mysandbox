name := "mysandbox"

version := "0.1"

scalaVersion := "2.12.4"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream" % "2.5.6",
  "org.scalaz" %% "scalaz-core" % "7.2.16",
  "com.typesafe.akka" %% "akka-stream-testkit" % "2.5.6" % Test
)

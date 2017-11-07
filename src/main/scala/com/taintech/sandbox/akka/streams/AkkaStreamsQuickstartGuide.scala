package com.taintech.sandbox.akka.streams

import akka.actor.ActorSystem
import akka.stream.Materializer

import scala.concurrent.ExecutionContext

trait AkkaStreamsQuickstartGuide {

  implicit val actorSystem: ActorSystem
  implicit val materializer: Materializer
  implicit val executionContext: ExecutionContext

}

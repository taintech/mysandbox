package com.taintech.sandbox.main

import java.nio.file.Paths

import akka.actor.ActorSystem
import akka.stream.scaladsl.{
  Broadcast,
  BroadcastHub,
  FileIO,
  Flow,
  GraphDSL,
  Keep,
  RunnableGraph,
  Sink,
  Source
}
import akka.stream._
import akka.util.ByteString
import akka.{Done, NotUsed}
import com.taintech.sandbox.akka.streams.AkkaStreamsQuickstartGuide

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scalaz._
import Scalaz._

object Main extends App with AkkaStreamsQuickstartGuide {

  implicit val actorSystem: ActorSystem = ActorSystem("sandbox")
  implicit val materializer: Materializer =
    ActorMaterializer(ActorMaterializerSettings(actorSystem), "flow")
  implicit val executionContext: ExecutionContext = actorSystem.dispatcher

  val source: Source[Int, NotUsed] = Source(1 to 100)
  lazy val done: Future[Done] = source.runForeach(i =>
    if (i < 100) println(i) else sys.error("Ouch"))(materializer)

  val factorials = source.scan(BigInt(1))((acc, next) => acc * next)

  lazy val result: Future[IOResult] =
    factorials
      .map(num => ByteString(s"$num\n"))
      .runWith(FileIO.toPath(Paths.get("factorials.txt")))

  final case class Author(handle: String)

  final case class Hashtag(name: String)

  final case class Tweet(author: Author, timestamp: Long, body: String) {
    def hashtags: Set[Hashtag] =
      body
        .split(" ")
        .collect {
          case t if t.startsWith("#") => Hashtag(t.replaceAll("[^#\\w]", ""))
        }
        .toSet
  }

  val akkaTag = Hashtag("#akka")

  val tweets: Source[Tweet, NotUsed] = Source(Tweet(Author("rolandkuhn"),
                                                    System.currentTimeMillis,
                                                    "#akka rocks!") ::
    Tweet(Author("patriknw"), System.currentTimeMillis, "#akka !") ::
    Tweet(Author("bantonsson"), System.currentTimeMillis, "#akka !") ::
    Tweet(Author("drewhk"), System.currentTimeMillis, "#akka !") ::
    Tweet(Author("ktosopl"), System.currentTimeMillis, "#akka on the rocks!") ::
    Tweet(Author("mmartynas"), System.currentTimeMillis, "wow #akka !") ::
    Tweet(Author("akkateam"), System.currentTimeMillis, "#akka rocks!") ::
    Tweet(Author("bananaman"), System.currentTimeMillis, "#bananas rock!") ::
    Tweet(Author("appleman"), System.currentTimeMillis, "#apples rock!") ::
    Tweet(Author("drama"),
          System.currentTimeMillis,
          "we compared #apples to #oranges!") ::
    Nil)

  val tweetsDone = tweets
    .map(_.hashtags) // Get all sets of hashtags ...
    .reduce(_ ++ _) // ... and reduce them to a single set, removing duplicates across all tweets
    .mapConcat(identity) // Flatten the stream of tweets to a stream of hashtags
    .map(_.name.toUpperCase) // Convert all hashtags to upper case
    .runWith(Sink.foreach(println)) // Attach the Flow to a Sink that will finally print the hashtags

  // $FiddleDependency org.akka-js %%% akkajsactorstream % 1.2.5.1

  def lineSink(filename: String): Sink[String, Future[IOResult]] =
    Flow[String]
      .map(s => ByteString(s + "\n"))
      .toMat(FileIO.toPath(Paths.get(filename)))(Keep.right)

  lazy val factorials2Result =
    factorials.map(_.toString).runWith(lineSink("factorial2.txt"))

  lazy val slowFacts = factorials
    .zipWith(Source(0 to 10))((num, idx) => s"$idx! = $num")
    .throttle(1, 100.millis, 1, ThrottleMode.shaping)
    .runForeach(println)

//  def writeAuthors: Sink[Author, NotUsed] = ???
//  def writeHashtags: Sink[Hashtag, NotUsed] = ???
//  val g = RunnableGraph.fromGraph(GraphDSL.create() { implicit b =>
//    import GraphDSL.Implicits._
//
//    val bcast = b.add(Broadcast[Tweet](2))
//    tweets ~> bcast.in
//    bcast.out(0) ~> Flow[Tweet].map(_.author) ~> writeAuthors
//    bcast.out(1) ~> Flow[Tweet].mapConcat(_.hashtags.toList) ~> writeHashtags
//    ClosedShape
//  })
//  g.run()

  val zipFutures = (done zip result) zip tweetsDone

  val monadResult = for {
    a <- done
    b <- result
    c <- tweetsDone
  } yield (a, b, c)

  val tick =
    Source.tick(1.seconds, 1.seconds, s"hello ${System.currentTimeMillis()}")

  val broadcastTick: RunnableGraph[Source[String, NotUsed]] =
    tick.toMat(BroadcastHub.sink(bufferSize = 256))(Keep.right)

  val sharedKillSwitch = KillSwitches.shared("my-kill-switch")
  val tickResults1 =
    tick.via(sharedKillSwitch.flow).runForeach(tick => println(s"I : $tick"))

//  val applicativeExp2 = done <*> (result <*> (tweetsDone <*> (factorials2Result <*> (slowFacts map identity))))

//  monadResult.onComplete(_ => actorSystem.terminate())
//  zipFutures.onComplete(_ => actorSystem.terminate())
//  applicativeExp.e.onComplete(_ => actorSystem.terminate())
  val applicativeExp = done |@| result |@| tweetsDone |@| factorials2Result |@| slowFacts |@| tickResults1
  applicativeExp.ff.onComplete(_ => actorSystem.terminate())

  val scheduled = KillSwitches.shared("my-kill-switch-scheduled")
  actorSystem.scheduler.scheduleOnce(4.seconds) {
    tick.via(scheduled.flow).runForeach(tick => println(s"II : $tick"))
  }
  actorSystem.scheduler.scheduleOnce(7.seconds) {
    println("Scheduled kill switch...")
    scheduled.shutdown()
  }
  actorSystem.scheduler.scheduleOnce(10.seconds) {
    println("Shared kill switch...")
    sharedKillSwitch.shutdown()
  }

}

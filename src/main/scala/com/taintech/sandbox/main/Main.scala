package com.taintech.sandbox.main

import java.nio.file.Paths

import akka.actor.ActorSystem
import akka.stream.scaladsl.{FileIO, Flow, Keep, Sink, Source}
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
  def done: Future[Done] = source.runForeach(i =>
    if (i < 100) println(i) else sys.error("Ouch"))(materializer)

  val factorials = source.scan(BigInt(1))((acc, next) => acc * next)

  def result: Future[IOResult] =
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

  def factorials2Result = factorials.map(_.toString).runWith(lineSink("factorial2.txt"))

  def slowFacts = factorials
    .zipWith(Source(0 to 10))((num, idx) => s"$idx! = $num")
    .throttle(1, 100.millis, 1, ThrottleMode.shaping)
    .runForeach(println)



  def monadResult = for {
    a <- done
    b <- result
    c <- tweetsDone
  } yield (a, b, c)

  val zipFutures = (done zip result) zip tweetsDone

  val applicativeFunctorScalaz = done |@| result |@| tweetsDone |@| factorials2Result |@| slowFacts

//  monadResult.onComplete(_ => actorSystem.terminate())
//  zipFutures.onComplete(_ => actorSystem.terminate())
  applicativeFunctorScalaz.e.onComplete(_ => actorSystem.terminate())
}

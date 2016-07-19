/*
 * Copyright 2015-2016 IBM Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package slack.whisk

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration._

import akka.Done
import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws._
import akka.http.scaladsl.unmarshalling._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.stream.ActorMaterializer
import akka.stream.OverflowStrategy
import akka.stream.QueueOfferResult
import akka.stream.scaladsl._

import spray.json._
import spray.json.DefaultJsonProtocol._

import java.util.concurrent.atomic.AtomicInteger

object Main {
    def main(args : Array[String]) : Unit = {
        implicit val system = ActorSystem()
        implicit val executionContext = system.dispatcher
        implicit val materializer = ActorMaterializer()

        val config = system.settings.config

        val token = config.getString("slack.bot.token")

        val rtmInfo = Await.result(SlackRTM.start(token), Duration.Inf)

        val botInfo = SlackRTMBotInfo(rtmInfo.selfId)

        val queuePromise = Promise[SourceQueue[Message]]
        val msgSource = Source.queue[Message](10, OverflowStrategy.dropNew).mapMaterializedValue { q =>
            queuePromise.trySuccess(q)
        }
        val futureQueue = queuePromise.future
        val outgoingMsgCounter = new AtomicInteger(1)
        val send: JsObject=>Unit = { msg =>
            futureQueue.onSuccess { case q =>
                val msgId = outgoingMsgCounter.getAndIncrement()
                val msgExt = JsObject(msg.fields + ("id" -> JsNumber(msgId)))
                println("Sending : " + msgExt.toString)
                q.offer(TextMessage(msgExt.toString)).map { _ match {
                    case QueueOfferResult.Enqueued =>
                        // fire-and-forget

                    case QueueOfferResult.Dropped =>
                        println("Message was dropped. Queue is full.")

                    case QueueOfferResult.QueueClosed =>
                        println("Queue is closed.")

                    case QueueOfferResult.Failure(f) =>
                        println("Queue failed: " + f.getMessage)
                }}
            }
        }

        val bots: List[SlackBot] = List(
            new ActionRunnerBot(send, botInfo)
        )

        val msgSink: Sink[Message, Future[Done]] = Sink.foreach {
            case message: TextMessage.Strict =>
                try {
                    // FIXME this swallows all errors in all "bots"
                    val parsed = JsonParser(message.text).asJsObject
                    bots.foreach(b => b.onMessage(parsed))
                } catch {
                    case t : Throwable =>
                    println("There was an error receiving messages: "+ t.getMessage())
                    println("Message was: " + message)
                }

            case x =>
                println("Dropping message: " + x)
                // drop it like it's hot
        }

        val flow: Flow[Message,Message,Future[Done]] =
            Flow.fromSinkAndSourceMat(msgSink, msgSource)(Keep.left)

        val (upgradeResponse, closed) =
            Http().singleWebSocketRequest(WebSocketRequest(rtmInfo.wsUrl), flow)

        val connected = upgradeResponse.map { upgrade =>
            if(upgrade.response.status == StatusCodes.SwitchingProtocols) {
                Done
            } else {
                throw new RuntimeException(s"Connection failed: ${upgrade.response.status}")
            }
        }

        connected.onComplete(println)
        closed.foreach { _ =>
            println("Closed. Shutting down...")
            shutItDown()
        }

        def shutItDown() = {
            materializer.shutdown()
            Await.ready(Http().shutdownAllConnectionPools(), Duration.Inf)
            system.terminate()
            Await.result(system.whenTerminated, Duration.Inf)
            System.exit(0)
        }

        // Say "stop" to stop.
        var input = ""
        while(input.trim != "stop") {
            println("Say 'stop' to stop.")
            input = scala.io.StdIn.readLine()
        }
        println("Shutting down...")
        shutItDown()
    }
}

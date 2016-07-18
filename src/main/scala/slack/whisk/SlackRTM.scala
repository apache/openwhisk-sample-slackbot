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

import scala.concurrent.Future

import akka.actor.ActorSystem
import akka.http.scaladsl._
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.stream.ActorMaterializer

import spray.json._
import spray.json.DefaultJsonProtocol._

case class SlackRTMBotInfo(
    selfId: String
)

case class SlackRTMInfo(
    wsUrl: String,
    selfId: String
)

object SlackRTM {
    def start(token: String)(implicit system: ActorSystem) : Future[SlackRTMInfo] = {
        implicit val executionContext = system.dispatcher
        implicit val materializer = ActorMaterializer()

        // Make an authenticated request to get the websocket endpoint and some more meta info.
        val request = HttpRequest(
            method = HttpMethods.GET,
            uri = Uri()
                .withScheme("https")
                .withHost(Uri.Host("slack.com"))
                .withPath(Uri.Path("/api/rtm.start"))
                .withQuery(Uri.Query("token" -> token))
        )

        for(
            response <- Http().singleRequest(request);
            jsObject <- Unmarshal(response.entity).to[JsObject]
        ) yield {
            SlackRTMInfo(
                jsObject.fields("url").convertTo[String],
                jsObject.fields("self").convertTo[JsObject].fields("id").convertTo[String]
            )
        }
    }
}

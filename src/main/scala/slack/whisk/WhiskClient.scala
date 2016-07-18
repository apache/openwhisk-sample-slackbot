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
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.marshalling._
import akka.http.scaladsl.unmarshalling._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.stream.ActorMaterializer

import spray.json._

/* A minimal non-blocking OpenWhisk client.
 * Currently supports only the endpoints required for the PoC Slackbot.
 */
class WhiskClient()(implicit val actorSystem: ActorSystem) {
    implicit private val executionContext = actorSystem.dispatcher
    implicit private val materializer = ActorMaterializer()

    private val config = actorSystem.settings.config

    private val apiHost   = config.getString("whisk.credentials.apihost")
    private val namespace = config.getString("whisk.credentials.namespace")
    private val auth      = config.getString("whisk.credentials.auth")
    private val username  = auth.split(":")(0)
    private val password  = auth.split(":")(1)

    private def authenticatedRequest(
        method: HttpMethod,
        path: Uri.Path,
        query: Uri.Query = Uri.Query.Empty,
        entity: RequestEntity = HttpEntity.Empty) = HttpRequest(
        method = method,
        uri = Uri()
            .withScheme("https")
            .withHost(apiHost)
            .withPath(path)
            .withQuery(query),
        headers = List(
            Authorization(BasicHttpCredentials(username, password)),
            Accept(MediaTypes.`application/json`)
        ),
        entity = entity
    )

    private def runRequest(futureRequest: Future[HttpRequest]) : Future[JsObject] = {
        for(
            request  <- futureRequest;
            response <- Http().singleRequest(request);
            jsVal    <- Unmarshal(response.entity).to[JsValue];
            jsObj    =  jsVal.asJsObject
        ) yield jsObj
    }

    def createJsAction(namePrefix: String, code: String) : Future[JsObject] = {
        val actionName = s"${namePrefix.replaceAll("/", "-")}-${scala.util.Random.nextInt(999999)}"

        val body = JsObject(
            "exec" -> JsObject(
                "kind" -> JsString("nodejs"),
                "code" -> JsString(code)
            )
        )

        runRequest(Marshal(body).to[MessageEntity].map { e =>
            authenticatedRequest(
                method = HttpMethods.PUT,
                path = Uri.Path(s"/api/v1/namespaces/$namespace/actions/$actionName"),
                entity = e
            )
        })
    }

    def invokeAction(name: String, args: JsObject) : Future[JsObject] = {
        runRequest(Marshal(args).to[MessageEntity].map { e =>
            authenticatedRequest(
                method = HttpMethods.POST,
                path   = Uri.Path(s"/api/v1/namespaces/$namespace/actions/$name"),
                query  = Uri.Query("blocking" -> "true", "result" -> "true"),
                entity = e
            )
        })
    }

    def deleteAction(name: String) : Future[JsObject] = {
        runRequest(Future.successful(
            authenticatedRequest(
                method = HttpMethods.DELETE,
                path   = Uri.Path(s"/api/v1/namespaces/$namespace/actions/$name")
            )
        ))
    }
}

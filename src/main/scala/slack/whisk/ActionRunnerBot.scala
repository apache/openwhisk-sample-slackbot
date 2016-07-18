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

import slack.whisk.util.JsParser

import scala.util.Try

import akka.actor.ActorSystem

import spray.json._
import spray.json.DefaultJsonProtocol._

/* Listen to private messages and mentions, and if they contain the keywords "run" and "please",
 * attempts to parse message as action code and action payload and create+run the action on the
 * configured Whisk deployment.
 */
class ActionRunnerBot(send: JsObject=>Unit, botInfo: SlackRTMBotInfo)(implicit val actorSystem: ActorSystem) extends SlackBot(send) {
    private implicit val executionContext = actorSystem.dispatcher

    private val whiskClient = new WhiskClient()

    override def onMessage(msg: JsObject) : Unit = msg.fields("type").convertTo[String] match {
        case "message" =>
            handleMessage(msg
                .fields("text").convertTo[String]
                .replaceAll("“", "\"")
                .replaceAll("”", "\"")
                , msg)

        case _ =>
    }

    private def handleMessage(text: String, msg: JsObject) : Unit = {
        val channel = msg.fields("channel").convertTo[String]
        val sender  = msg.fields("user").convertTo[String]

        val shouldConsider = channel.startsWith("D") || text.contains(s"<@${botInfo.selfId}>")

        if(!shouldConsider)
            return

        if(text.contains("run") && text.contains("please")) {
            val codeBlockPattern = """```([^`]+)```""".r

            val matches = codeBlockPattern.findAllIn(text).map(s => s.substring(3, s.length - 3)).toList

            if(matches.length != 2) {
                sendMessage(channel, s"Sorry, <@$sender>, I'd love to run your action but this requires exactly two code blocks (one for the JS code, one for the `args` JSON object).")
                return
            }

            val codeStr = matches(0)
            val argsStr = matches(1)

            val parseResult = JsParser.parse(codeStr)
            if(parseResult.isLeft) {
                sendMessage(channel, s"Sorry, <@$sender>, I couldn't parse your JavaScript code:\n```${parseResult.left.get}```")
                return
            }

            val args = Try(JsonParser(argsStr).asJsObject)

            if(args.isFailure) {
                sendMessage(channel, s"Sorry, <@$sender>, I couldn't parse your second code block as a JSON object.")
                return
            }

            sendMessage(channel, "Creating action...")
            whiskClient.createJsAction("slack-action", codeStr).map { obj =>
                val name = obj.fields("name").convertTo[String]
                sendMessage(channel, "Invoking action...")
                whiskClient.invokeAction(name, args.get).map { res =>
                    sendMessage(channel, s"```${res.prettyPrint}```")
                    sendMessage(channel, s"There you go, <@$sender>.")
                }.recover { case f =>
                    sendMessage(channel, s"Sorry, <@$sender>, action invocation failed with: `${f.getMessage}`.")
                }.andThen { case _ =>
                    whiskClient.deleteAction(name)
                }
            }.recover { case f =>
                sendMessage(channel, s"Sorry, <@$sender>, action creation failed with: `${f.getMessage}`.")
            }
        }
    }

    private def sendMessage(channel: String, text: String) : Unit = {
        send(JsObject(
            "type" -> JsString("message"),
            "channel" -> JsString(channel),
            "text" -> JsString(text)
        ))
    }
}

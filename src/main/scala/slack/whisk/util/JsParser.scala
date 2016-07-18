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
package slack.whisk.util

import scala.util.Try

import jdk.nashorn.internal.parser.Parser
import jdk.nashorn.internal.runtime._
import jdk.nashorn.internal.runtime.options.Options

import java.io.PrintWriter
import java.io.StringWriter

/* Relies on Java 8 internal JS engine to detect parse errors before the action
 * is created.
 */
object JsParser {
    def parse(code: String) : Either[String,Unit] = {
        val options = new Options("nashorn")
        val ctx = new Context(options, new ErrorManager(), Thread.currentThread.getContextClassLoader)
        val src = Source.sourceFor("action-code", code)
        val errorWriter = new StringWriter()
        val errors = new ErrorManager(new PrintWriter(errorWriter))
        val parser = new Parser(ctx.getEnv(), src, errors)

        try {
            parser.parse()
            if(errors.hasErrors()) {
                errorWriter.flush()
                Left(errorWriter.getBuffer().toString())
            } else {
                Right(())
            }
        } catch {
            case e: Throwable =>
                Left(e.getMessage)
        }
    }
}

/*
 * Copyright (C) 2011 Mathias Doenitz
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
package cc.spray

import akka.actor.ActorRef
import utils.ActorHelpers

/**
 * A specialized [[cc.spray.RootService]] for connector-less deployment on top of the ''spray-can'' `HttpServer`.
 */
class SprayCanRootService(firstService: ActorRef, moreServices: ActorRef*)
        extends RootService(firstService, moreServices: _*) with SprayCanSupport {

  lazy val timeoutActor = ActorHelpers.actor(SpraySettings.TimeoutActorId)

  protected override def receive = {
    case context: can.RequestContext => {
      import context._
      val complete: can.HttpResponse => Unit = responder.complete
      try {
        handler(fromSprayCanContext(request, remoteAddress, complete))
      } catch handleExceptions(request, complete)
    }
    case can.Timeout(method, uri, protocol, headers, remoteAddress, complete) => {
      val request = can.HttpRequest(method, uri, headers)
      try {
        if (self == timeoutActor)
          complete(fromSprayResponse(timeoutResponse(fromSprayCanRequest(request))))
        else
          timeoutActor ! Timeout(fromSprayCanContext(request, remoteAddress, complete))
      } catch handleExceptions(request, complete)
    }
  }

  protected def handleExceptions(request: can.HttpRequest,
                                 complete: can.HttpResponse => Unit): PartialFunction[Throwable, Unit] = {
    case e: Exception => complete(fromSprayResponse(responseForException(request, e)))
  }
}
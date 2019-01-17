package com.example

import akka.actor.{ ActorRef, ActorSystem }
import akka.event.Logging
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport

import scala.concurrent.duration._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.MethodDirectives.delete
import akka.http.scaladsl.server.directives.MethodDirectives.get
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.http.scaladsl.server.directives.PathDirectives.path

import scala.concurrent.{ Await, Future }
import com.example.DBValueRegistryActor._
import akka.pattern.ask
import akka.util.Timeout

trait DBRoutes extends JsonSupport {

  implicit def system: ActorSystem

  lazy val log = Logging(system, classOf[DBRoutes])

  def dbvalueRegistryActor: ActorRef

  implicit lazy val timeout = Timeout(5.seconds)

  lazy val dbRoutes: Route =
    pathPrefix("proxy") {
      concat(
        pathPrefix("newnode") {
          concat(
            put {
              entity(as[ServerURI]) { serverUri =>
                dbvalueRegistryActor ! AddNode(serverUri)
                log.info("New node added! -> " + serverUri.uri)
                complete(StatusCodes.Created)
              }
            }
          )
        },

        path(Segment) { id =>
          concat(
            get {
              val response: Future[HttpResponse] = (dbvalueRegistryActor ? GetValue(id)).mapTo[HttpResponse]
              complete(response)
            },

            put {
              entity(as[Value]) { value =>
                val valueCreated: Future[HttpResponse] = (dbvalueRegistryActor ? PutValue(id, value)).mapTo[HttpResponse]
                complete(valueCreated)
              }
            },

            delete {
              val valueDeleted: Future[HttpResponse] = (dbvalueRegistryActor ? DeleteValue(id)).mapTo[HttpResponse]
              complete(valueDeleted)
            }
          )
        }

      )
    }

}

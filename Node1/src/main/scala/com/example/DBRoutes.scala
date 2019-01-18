package com.example

import akka.actor.{ ActorRef, ActorSystem }
import akka.event.Logging

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{ Success, Failure }

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.MethodDirectives.delete
import akka.http.scaladsl.server.directives.MethodDirectives.get
import akka.http.scaladsl.server.directives.MethodDirectives.post
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.http.scaladsl.server.directives.PathDirectives.path

import scala.concurrent.{ Future, Await }
import com.example.DBValueRegistryActor._
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.ExecutionContext.Implicits.global

trait DBRoutes extends JsonSupport {

  implicit def system: ActorSystem

  lazy val log = Logging(system, classOf[DBRoutes])

  def dbvalueRegistryActor: ActorRef

  implicit lazy val timeout = Timeout(5.seconds)

  lazy val dbRoutes: Route =
    pathPrefix("values") {
      concat(
        pathPrefix("slave") {
          concat(
            put {
              entity(as[ServerURI]) { uri =>
                dbvalueRegistryActor ! AddSlave(uri)
                log.info("Get new slave: " + uri.uri)
                complete(StatusCodes.OK)
              }
            }
          )
        },

        pathPrefix("resharding") {
          concat(
            path(Segment) { countOfServersWithNum =>
              get {
                dbvalueRegistryActor ! Resharding(countOfServersWithNum)
                complete(StatusCodes.OK)
              }
            }
          )
        },

        path(Segment) { id =>
          concat(
            get {
              val maybeValue: Future[Option[Value]] = (dbvalueRegistryActor ? GetValue(id.toInt)).mapTo[Option[Value]]
              rejectEmptyResponse {
                complete(maybeValue)
              }
            },

            put {
              entity(as[Value]) { value =>
                val valueCreated: Future[ActionPerformed] = (dbvalueRegistryActor ? PutValue(id.toInt, value)).mapTo[ActionPerformed]
                onSuccess(valueCreated) { performed =>
                  log.info("Created value [{}]: {}", id, performed.description)
                  complete((StatusCodes.Created, performed))
                }
              }
            },

            delete {
              val valueDeleted: Future[ActionPerformed] = (dbvalueRegistryActor ? DeleteValue(id.toInt)).mapTo[ActionPerformed]
              onSuccess(valueDeleted) { performed =>
                log.info("Deleted value [{}]: {}", id, performed.description)
                complete((StatusCodes.OK, performed))
              }
            }
          )
        }

      )
    }

}

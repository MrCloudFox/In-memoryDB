package com.example

import java.io.{ BufferedWriter, FileWriter, PrintWriter }

import com.example.QuickstartServer._

import scala.util.{ Failure, Success }
import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.util.Timeout

import scala.concurrent.Await
import scala.concurrent.duration._

final case class Value(value: String)
final case class Values(values: Seq[(String, Value)])
final case class ServerURI(uri: String)

object CommunicationDBActor {
  def props: Props = Props[CommunicationDBActor]

  final case class ActionPerformed(description: String)
  final case class PutValue(id: String, value: Value)
  final case class GetValue(id: String)
  final case class DeleteValue(id: String)
  final case class AddNode(serverUri: ServerURI)
}

class CommunicationDBActor extends Actor with ActorLogging {
  import CommunicationDBActor._

  implicit lazy val timeout = Timeout(5.seconds)

  var countOfServers = 0
  var nodes = new scala.collection.mutable.HashMap[Int, ServerURI].withDefaultValue(null)

  def sharding(id: Int): Int = {
    var serverId = countOfServers
    while (id % serverId != 0) {
      serverId -= 1
    }
    serverId - 1
  }

  def resharding = {
    for ((k, v) <- nodes) {
      Http().singleRequest(HttpRequest(method = HttpMethods.GET, uri = v.uri + "/values/resharding/" + countOfServers + "=>" + (k + 1)))
        .onComplete {
          case Success(res) => log.info("Successfull send request to resharding! " + v.uri + "/values/resharding/" + countOfServers + "=>" + (k + 1))
        }
    }
  }

  def sendReplicationFollowers(masterServerUri: ServerURI) = {
    if (master_slaves.contains(masterServerUri)) {
      for (slave <- master_slaves(masterServerUri)) {
        Http().singleRequest(HttpRequest(method = HttpMethods.PUT, uri = masterServerUri.uri + "/values/slave", entity = Await.result(Marshal(slave).to[RequestEntity], 2 second)))
          .onComplete {
            case Success(res) => log.info("Successfull send request to add slave! " + masterServerUri.uri + "/values/slave")
          }
      }
    }
  }

  def putValue(sender: ActorRef, id: Int, uri: String, value: Value): Unit = {
    Http().singleRequest(HttpRequest(method = HttpMethods.PUT, uri = uri + "/values/" + id, entity = Await.result(Marshal(value).to[RequestEntity], 2 second)))
      .onComplete {
        case Success(res) => sender ! res
        case Failure(_) => sender ! "Something wrong"
      }
  }

  def getValue(sender: ActorRef, id: Int, uri: String): Boolean = {
    Http().singleRequest(HttpRequest(method = HttpMethods.GET, uri = uri + "/values/" + id))
      .onComplete {
        case Success(res) => {
          sender ! res
          true
        }
        case Failure(_) => {
          sender ! "Something wrong"
          false
        }
      }
    false
  }

  def deleteValue(sender: ActorRef, id: Int, uri: String): Unit = {
    Http().singleRequest(HttpRequest(method = HttpMethods.DELETE, uri = uri + "/values/" + id))
      .onComplete {
        case Success(res) => sender ! res
        case Failure(_) => sender ! "Something wrong"
      }
  }

  def receive: Receive = {
    case PutValue(id, value) =>
      val actorRef = sender
      putValue(actorRef, id.toInt, nodes(sharding(id.toInt)).uri, value)
      if (typeOfReplication == TypeOfReplication.Sync && master_slaves.contains(nodes(sharding(id.toInt)))) {
        for (slave <- master_slaves(nodes(sharding(id.toInt)))) { //may be need to fix
          putValue(actorRef, id.toInt, slave.uri, value)
        }
      }
    case GetValue(id) =>
      var actorRef = sender
      Http().singleRequest(HttpRequest(method = HttpMethods.GET, uri = nodes(sharding(id.toInt)).uri + "/values/" + id))
        .onComplete {
          case Success(res) => {
            if (typeOfReplication == TypeOfReplication.Sync && res.status == StatusCodes.InternalServerError) {
              if (master_slaves.contains(nodes(sharding(id.toInt)))) {
                for (slave <- master_slaves(nodes(sharding(id.toInt)))) { //may be need to fix
                  if (actorRef != null && getValue(actorRef, id.toInt, slave.uri)) actorRef = null // not well
                }
              }
            } else {
              actorRef ! res
            }
          }
          case Failure(_) => actorRef ! "Something wrong"
        }
    case DeleteValue(id) =>
      val actorRef = sender
      deleteValue(actorRef, id.toInt, nodes(sharding(id.toInt)).uri)
      if (typeOfReplication == TypeOfReplication.Sync && master_slaves.contains(nodes(sharding(id.toInt)))) {
        for (slave <- master_slaves(nodes(sharding(id.toInt)))) { //may be need to fix
          deleteValue(actorRef, id.toInt, slave.uri)
        }
      }
    case AddNode(serverUri) =>
      countOfServers += 1
      nodes(countOfServers - 1) = serverUri
      if (typeOfReplication == TypeOfReplication.Async) {
        sendReplicationFollowers(serverUri)
      }
      resharding
  }
}
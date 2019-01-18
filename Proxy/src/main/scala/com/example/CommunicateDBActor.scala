package com.example

import java.io.{ BufferedWriter, FileWriter, PrintWriter }

import com.example.QuickstartServer._

import scala.util.{ Failure, Success }
import akka.actor.{ Actor, ActorLogging, Props }
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.util.Timeout

import scala.concurrent.Await
import scala.concurrent.duration._

final case class Value(value: String)
final case class Values(values: Seq[(String, Value)])
final case class ServerURI(uri: String)

object CommunicateDBActor {
  def props: Props = Props[CommunicateDBActor]

  final case class ActionPerformed(description: String)
  final case class PutValue(id: String, value: Value)
  final case class GetValue(id: String)
  final case class DeleteValue(id: String)
  final case class AddNode(serverUri: ServerURI)
}

class CommunicateDBActor extends Actor with ActorLogging {
  import CommunicateDBActor._

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

  def receive: Receive = {
    case PutValue(id, value) =>
      val actorRef = sender()
      Http().singleRequest(HttpRequest(method = HttpMethods.PUT, uri = nodes(sharding(id.toInt)).uri + "/values/" + id, entity = Await.result(Marshal(value).to[RequestEntity], 2 second)))
        .onComplete {
          case Success(res) => actorRef ! res
          case Failure(_) => actorRef ! "Something wrong"
        }
    case GetValue(id) =>
      val actorRef = sender()
      Http().singleRequest(HttpRequest(method = HttpMethods.GET, uri = nodes(sharding(id.toInt)).uri + "/values/" + id))
        .onComplete {
          case Success(res) => actorRef ! res
          case Failure(_) => actorRef ! "Something wrong"
        }
    case DeleteValue(id) =>
      val actorRef = sender()
      Http().singleRequest(HttpRequest(method = HttpMethods.DELETE, uri = nodes(sharding(id.toInt)).uri + "/values/" + id))
        .onComplete {
          case Success(res) => actorRef ! res
          case Failure(_) => actorRef ! "Something wrong"
        }
    case AddNode(serverUri) =>
      countOfServers += 1
      nodes(countOfServers - 1) = serverUri
      resharding
  }
}
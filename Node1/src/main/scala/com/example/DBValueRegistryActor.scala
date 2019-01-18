package com.example

import com.example.QuickstartServer._
import java.io.File
import java.io.{ BufferedWriter, FileWriter, PrintWriter }

import akka.actor.{ Actor, ActorLogging, Props }
import java.io._
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.Files.copy
import java.nio.file.Paths.get

import scala.concurrent.duration._
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{ HttpMethods, HttpRequest, RequestEntity }

import scala.concurrent.Await
import scala.language.postfixOps
import scala.util.{ Failure, Success }

final case class Value(value: String)
final case class Values(values: Seq[(Int, Value)])
final case class ServerURI(uri: String)

object DBValueRegistryActor {
  def props: Props = Props[DBValueRegistryActor]

  final case class ActionPerformed(description: String)
  final case object GetValues
  final case class PutValue(id: Int, value: Value)
  final case class GetValue(id: Int)
  final case class DeleteValue(id: Int)
  final case object RecoveryDB
  final case object SynchronizeWithFile
  final case class Resharding(countOfServers: String)
  final case class AddSlave(slaveUri: ServerURI)
}

class DBValueRegistryActor extends Actor with ActorLogging {
  import DBValueRegistryActor._

  var values = new scala.collection.mutable.HashMap[Int, Value].withDefaultValue(null)
  var slaves = new scala.collection.mutable.ArrayBuffer[ServerURI]

  def receive: Receive = {
    case GetValues =>
      sender() ! Values(values.toSeq)
    case PutValue(id, value) =>
      if (values(id) != value) {
        values(id) = value
        for (slave <- slaves) {
          Http().singleRequest(HttpRequest(method = HttpMethods.PUT, uri = slave.uri + "/values/" + id, entity = Await.result(Marshal(value).to[RequestEntity], 2 second)))
        }
        sender() ! ActionPerformed(s"Value ${id} created.")
      }
    case GetValue(id) =>
      sender() ! Option(values(id))
    case DeleteValue(id) =>
      if (values.contains(id)) {
        values -= id
        for (slave <- slaves) {
          Http().singleRequest(HttpRequest(method = HttpMethods.DELETE, uri = slave.uri + "/values/" + id))
        }
        sender() ! ActionPerformed(s"User ${id} deleted.")
      }
    case RecoveryDB =>
      for (
        (k, v) <- io.Source.fromFile("recovery.txt").getLines().filter(line => line.nonEmpty).map {
          l =>
            val Array(key, value) = l.split("->")
            key -> new Value(value)
        }.toMap
      ) {
        values(k.toInt) = v
      }
    case SynchronizeWithFile =>
      val outFile = new PrintWriter(new BufferedWriter(new FileWriter("temp.txt")))
      for ((k, v) <- values) outFile.println(k + "->" + v.value)
      outFile.close
      implicit def toPath(filename: String) = get(filename)
      copy("temp.txt", "recovery.txt", REPLACE_EXISTING)
    case Resharding(countOfServersWithNum) =>
      val proxy = "http://127.0.0.1:8080"
      var countOfServers = countOfServersWithNum.split("=>")(0).toInt
      while (countOfServers > countOfServersWithNum.split("=>")(1).toInt) {
        for ((k, v) <- values) {
          if (k % countOfServers == 0) {
            Http().singleRequest(HttpRequest(method = HttpMethods.PUT, uri = proxy + "/proxy/" + k, entity = Await.result(Marshal(v).to[RequestEntity], 2 second)))
              .onComplete {
                case Success(res) => log.info("Send Value: " + k) //sender() ! res
                case Failure(_) => sender() ! "Something wrong"
              }
            values -= k
          }
        }
        countOfServers -= 1
      }
    case AddSlave(slaveUri: ServerURI) =>
      slaves += slaveUri
  }
}
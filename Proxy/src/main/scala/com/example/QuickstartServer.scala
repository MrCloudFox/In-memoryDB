package com.example

import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.concurrent.duration.Duration
import scala.util.{ Failure, Success }

import akka.actor.{ ActorRef, ActorSystem }
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer

object QuickstartServer extends App with ProxyRoutes {

  implicit val system: ActorSystem = ActorSystem("helloAkkaHttpServer")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContext = system.dispatcher

  val typeOfReplication = TypeOfReplication.Sync // or Async

  val master_slaves = new scala.collection.mutable.HashMap[ServerURI, List[ServerURI]] // Master URI -> Slave URL

  master_slaves(new ServerURI("http://localhost:8081")) = List(new ServerURI("http://localhost:8082"), new ServerURI("http://localhost:8083"))
  master_slaves(new ServerURI("http://localhost:8082")) = List(new ServerURI("http://localhost:8083"))
  master_slaves(new ServerURI("http://localhost:8083")) = List(new ServerURI("http://localhost:8082"))

  val communicateDbActor: ActorRef = system.actorOf(CommunicationDBActor.props, "DBRegistryActor")

  lazy val routes: Route = proxyRoutes

  val serverBinding: Future[Http.ServerBinding] = Http().bindAndHandle(routes, "localhost", 8080)

  serverBinding.onComplete {
    case Success(bound) =>
      println(s"Server online at http://\\${bound.localAddress.getHostString}:\\${bound.localAddress.getPort}/")
    case Failure(e) =>
      Console.err.println(s"Server could not start!")
      e.printStackTrace()
      system.terminate()
  }

  Await.result(system.whenTerminated, Duration.Inf)
}
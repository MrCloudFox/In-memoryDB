package com.example

import com.example.DBValueRegistryActor.{ RecoveryDB, SynchronizeWithFile }

import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.concurrent.duration.Duration
import scala.util.{ Failure, Success }
import akka.actor.{ ActorRef, ActorSystem }
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{ HttpMethods, HttpRequest, RequestEntity }
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer

import scala.concurrent.duration._

object QuickstartServer extends App with DBRoutes {

  implicit val system: ActorSystem = ActorSystem("helloAkkaHttpServer")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContext = system.dispatcher
  val proxy = "localhost:8080"
  val address = "localhost"
  val port = 8081
  Http().singleRequest(HttpRequest(method = HttpMethods.PUT, uri = "http://" + proxy + "/proxy/newnode", entity = Await.result(Marshal(new ServerURI("http://" + address + ":" + port)).to[RequestEntity], (10 second))))

  val dbvalueRegistryActor: ActorRef = system.actorOf(DBValueRegistryActor.props, "DBRegistryActor")
  dbvalueRegistryActor ! RecoveryDB

  lazy val routes: Route = dbRoutes

  val serverBinding: Future[Http.ServerBinding] = Http().bindAndHandle(routes, address, port)

  serverBinding.onComplete {
    case Success(bound) =>
      println(s"Server online at http://\\${bound.localAddress.getHostString}:\\${bound.localAddress.getPort}/")
      system.scheduler.schedule(Duration.Zero, 5 seconds) {
        dbvalueRegistryActor ! SynchronizeWithFile
      }
    case Failure(e) =>
      Console.err.println(s"Server could not start!")
      e.printStackTrace()
      system.terminate()
  }

  Await.result(system.whenTerminated, Duration.Inf)
}
package com.example

import com.example.CommunicateDBActor.ActionPerformed
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol

trait JsonSupport extends SprayJsonSupport {
  import DefaultJsonProtocol._

  implicit val userJsonFormat = jsonFormat1(Value)
  implicit val usersJsonFormat = jsonFormat1(Values)
  implicit val serverURI = jsonFormat1(ServerURI)
  implicit val actionPerformedJsonFormat = jsonFormat1(ActionPerformed)
}
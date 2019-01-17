package com.example

import com.example.DBValueRegistryActor.ActionPerformed

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {

  implicit val userJsonFormat = jsonFormat1(Value)
  implicit val usersJsonFormat = jsonFormat1(Values)
  implicit val serverUri = jsonFormat1(ServerURI)
  implicit val actionPerformedJsonFormat = jsonFormat1(ActionPerformed)
}
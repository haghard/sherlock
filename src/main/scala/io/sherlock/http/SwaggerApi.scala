package io.sherlock.http

/*
import akka.http.scaladsl.model.HttpMethods
import net.jtownson.swakka.OpenApiModel.{ OpenApi, Operation, PathItem }
import net.jtownson.swakka.RouteGen
import net.jtownson.swakka.routegen.SwaggerRouteSettings
import net.jtownson.swakka.model.Responses.ResponseValue
import net.jtownson.swakka.jsonschema.SchemaWriter._
import net.jtownson.swakka.OpenApiJsonProtocol._
 */

/*

import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import net.jtownson.swakka.openapimodel._
import net.jtownson.swakka.openapijson._
import net.jtownson.swakka.coreroutegen._
import net.jtownson.swakka.openapiroutegen._
import shapeless.{::, HNil}

//https://bitbucket.org/jtownson/swakka
class SwaggerApi {
  type NoParams = HNil
  type StringResponse = ResponseValue[String, HNil]
  type Paths = PathItem[NoParams, StringResponse] :: HNil

  private val swaggerDescriptor =
    OpenApi(paths =
      PathItem[NoParams, Route, StringResponse](
        path = "/ping",
        method = HttpMethods.GET,
        operation = Operation[NoParams, StringResponse](
          responses = ResponseValue[String, HNil]("200", "ok"),
          endpointImplementation = myRoute)) :: HNil)

  val myRoute = { (param: NoParams) ⇒
    complete("pong")
  }

  val route =
    RouteGen.openApiRoute(swaggerDescriptor, swaggerRouteSettings = Some(SwaggerRouteSettings()))
}
 */

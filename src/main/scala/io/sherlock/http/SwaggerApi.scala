package io.sherlock.http

import akka.http.scaladsl.model.HttpMethods
import net.jtownson.swakka.OpenApiModel.{ OpenApi, Operation, PathItem }
import net.jtownson.swakka.RouteGen
import net.jtownson.swakka.routegen.SwaggerRouteSettings
import shapeless.{ ::, HNil }
import akka.http.scaladsl.server.Directives._
import net.jtownson.swakka.model.Responses.ResponseValue
import net.jtownson.swakka.jsonschema.SchemaWriter._
import net.jtownson.swakka.OpenApiJsonProtocol._
import shapeless.{ ::, HNil }

//https://bitbucket.org/jtownson/swakka
class SwaggerApi {
  type NoParams = HNil
  type StringResponse = ResponseValue[String, HNil]
  type Paths = PathItem[NoParams, StringResponse] :: HNil

  private val api =
    OpenApi(paths =
      PathItem[NoParams, StringResponse](
        path = "/ping",
        method = HttpMethods.GET,
        operation = Operation[NoParams, StringResponse](
          responses = ResponseValue[String, HNil]("200", "ok"),
          endpointImplementation = { (param: NoParams) ⇒ complete("pong") })) :: HNil)

  val route =
    RouteGen.openApiRoute(api, swaggerRouteSettings = Some(SwaggerRouteSettings()))
}
package io.sherlock.stages

import akka.http.scaladsl.model.HttpEntity.Strict
import akka.http.scaladsl.model._
import akka.stream.{ Attributes, Outlet, Inlet, FlowShape }
import akka.stream.stage.{ OutHandler, InHandler, GraphStageLogic, GraphStage }
import com.twitter.algebird.BloomFilterMonoid

/*

curl -X POST -d 'keyword:abracadabra' --header "Content-Type:application/json" http://192.168.178.99:9091/put --include
*/
class BloomFilterStage(val numHashes: Int = 8, val width: Int = 1024) extends GraphStage[FlowShape[HttpRequest, HttpResponse]] {
  private val path = "/contains/"
  private val pathPref = Uri.Path(path)

  private val extractor = """keyword:(\w+)""".r
  val in = Inlet[HttpRequest]("bf.in")
  val out = Outlet[HttpResponse]("bf.out")

  override val shape = FlowShape.of(in, out)

  val M = new BloomFilterMonoid[String](numHashes, width)
  var bloomFilter = M.zero

  override def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) {
    val requestHandler: HttpRequest ⇒ HttpResponse = {
      case HttpRequest(HttpMethods.POST, Uri.Path("/put"), headers, Strict(ContentTypes.`text/plain(UTF-8)`, data), HttpProtocols.`HTTP/1.1`) ⇒
        data.utf8String match {
          case extractor(keyword) ⇒
            println(s"add to bloom-filter: $keyword")
            bloomFilter = bloomFilter + keyword
            HttpResponse(StatusCodes.OK, entity = s"$keyword has been added")
          case _ ⇒
            HttpResponse(StatusCodes.BadRequest)
        }
      case HttpRequest(HttpMethods.GET, uri, headers, entity, HttpProtocols.`HTTP/1.1`) if (uri.path.startsWith(pathPref)) ⇒
        val keyword = uri.path.toString.replace(path, "")
        println(s"ask for $keyword")
        val apxBool = (bloomFilter contains keyword)
        if (apxBool.isTrue) HttpResponse(entity = s"[$keyword] contains with probability: ${apxBool.withProb}")
        else HttpResponse(StatusCodes.NotFound)

      //curl -X GET -d 'success' --header "Content-Type:text/plain" http://localhost:9000/contains

      case HttpRequest(HttpMethods.GET, Uri.Path("/check"), headers, Strict(ContentTypes.`text/plain(UTF-8)`, data), HttpProtocols.`HTTP/1.1`) ⇒
        val elem = data.utf8String
        println(s"Contains: $elem")
        val apxBool = bloomFilter.contains(elem)
        HttpResponse(entity = s"Contains: $apxBool")
    }

    setHandler(in, new InHandler {
      override def onPush(): Unit = {
        val req = grab(in)
        push(out, requestHandler(req))
      }
    })

    setHandler(out, new OutHandler {
      override def onPull(): Unit = {
        pull(in)
      }
    })
  }
}
package io.sherlock

import scala.concurrent.Future
import akka.http.scaladsl.server.{ RequestContext, Route, RouteResult }

//https://medium.com/iterators/extending-requestcontext-in-akka-http-for-fun-and-profit-ceb056964758
package object core {

  case class TenantRequestContext(tenantId: Long, ctx: RequestContext)

  type TenantRoute = TenantRequestContext ⇒ Future[RouteResult]

  object TenantRequestContext {
    implicit def jwtRequestContextIsRequestContext(tenantRequestContext: TenantRequestContext): RequestContext =
      tenantRequestContext.ctx
  }

  implicit def _routeSubtyping1(route: Route): TenantRoute = ctx ⇒ route(ctx)

  abstract class TenantDirective[T] {
    def tapply(f: T ⇒ TenantRoute): TenantRoute
  }

  type TenantDirective0 = TenantDirective[Unit]
  type TenantDirective1[T] = TenantDirective[T]

  /*private case object ExtractDecoded extends TenantDirective1[Decoded] {
    override def tapply(inner: (Decoded) => TenantRoute): TenantRoute =
      ctx => inner(ctx.decoded)(ctx)
  }*/
  /*
  private case object ExtractClaims extends JwtDirective1[Claims] {
    override def tapply(inner: (Claims) => JwtRoute): JwtRoute = ctx => inner(ctx.decoded.claims)(ctx)
  }
  */
}

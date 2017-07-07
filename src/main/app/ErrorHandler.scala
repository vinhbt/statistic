package app

import javax.inject.{Inject, Provider, Singleton}

import play.api._
import play.api.http.{DefaultHttpErrorHandler, MimeTypes}
import play.api.mvc.{RequestHeader, Result}
import play.api.routing.Router
import sd.playcommon.JS

import scala.concurrent.Future

@Singleton
class ErrorHandler @Inject()(
  env:          Environment,
  config:       Configuration,
  sourceMapper: OptionalSourceMapper,
  router:       Provider[Router]
)
    extends DefaultHttpErrorHandler(env, config, sourceMapper, router) {

  override def onBadRequest(req: RequestHeader, error: String): Future[Result] = {
    if (req.accepts(MimeTypes.JSON)) Future successful JS.InvalidData
    else super.onBadRequest(req, error)
  }
}

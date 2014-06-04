package controllers

import play.api.mvc._
import akka.pattern.ask
import queue._
import play.api.libs.json.{JsValue, JsObject, Json}
import akka.util.Timeout
import java.util.concurrent.TimeUnit
import scala.concurrent.{Future, ExecutionContext}
import queue.Size
import queue.QueueDeleted
import queue.Added
import queue.QueueCreated
import queue.QueueSize
import queue.Clear
import queue.DeleteQueue
import queue.Append
import queue.Cleared
import queue.CreateQueue
import queue.Poll
import queue.Blob
import java.nio.charset.Charset
import play.api.{Mode, Play}
import tools.Constants

object Application extends Controller {

  implicit val timeout = Constants.bigTimeout
  implicit val ec: ExecutionContext = QueuesManager.system().dispatcher
  val charset = Charset.forName("UTF-8")

  def Secured[A](username: String, password: String)(action: => Result) = Action { request =>
    Play.current.mode match {
      case Mode.Dev => action
      case Mode.Test => action
      case Mode.Prod => request.headers.get("Authorization").flatMap { authorization =>
        authorization.split(" ").drop(1).headOption.filter { encoded =>
          new String(org.apache.commons.codec.binary.Base64.decodeBase64(encoded.getBytes)).split(":").toList match {
            case u :: p :: Nil if u == username && password == p => true
            case _ => false
          }
        }.map(_ => action)
      }.getOrElse {
        Unauthorized.withHeaders("WWW-Authenticate" -> """Basic realm="Secured"""")
      }
    }
  }

  def ApiAction(token: String)(action: => Future[Result]) = Action.async { request =>
    Play.current.mode match {
      case Mode.Dev => action.map(_.withHeaders("Access-Control-Allow-Origin" -> "*"))
      case Mode.Test => action.map(_.withHeaders("Access-Control-Allow-Origin" -> "*"))
      case Mode.Prod => request.headers.get("AuthToken").filter(t => token == t)
        .fold(Future.successful(Unauthorized("")))(_ => action.map(_.withHeaders("Access-Control-Allow-Origin" -> "*")))
    }
  }

  def JsonApiAction(token: String)(action: Request[JsValue] => Future[Result]) = Action.async(parse.json) { request =>
    Play.current.mode match {
      case Mode.Dev => action(request)map(_.withHeaders("Access-Control-Allow-Origin" -> "*"))
      case Mode.Test => action(request)map(_.withHeaders("Access-Control-Allow-Origin" -> "*"))
      case Mode.Prod => request.headers.get("AuthToken").filter(t => token == t)
        .fold(Future.successful(Unauthorized("")))(_ => action(request).map(_.withHeaders("Access-Control-Allow-Origin" -> "*")))
    }
  }

  def preflightMetrics = preflight
  def preflightQueue(name: String) = preflight
  def preflightClear(name: String) = preflight
  def preflightSize(name: String) = preflight

  def preflight = Action {
    Ok.withHeaders(
      "Access-Control-Allow-Origin" -> "*",
      "Access-Control-Allow-Methods" -> "POST, PUT, DELETE, GET, OPTIONS",
      "Access-Control-Allow-Headers" -> "AuthToken, Content-Type"
    )
  }

  def index = Secured("admin", Constants.password) {
    Ok(views.html.index("Your new application is ready."))
  }

  def stats = Action {
    Ok(JsonReporter.toJson(MetricsStats.metrics()))
  }

  import tools.implicits.debug.futureKcombine

  def append(name: String) = JsonApiAction(Constants.token) { request =>
    val context = MetricsStats.responsesTime().time()
    val context2 = MetricsStats.responsesWriteTime().time()
    (QueuesManager.master() ? Append(name, request.body.as[JsObject]))
      .mapTo[Added].map(added => Ok(Json.obj("id" -> added.id)))
      .thenCombine { _ =>
        context.close()
        context2.close()
      }
  }

  def size(name: String) = ApiAction(Constants.token) {
    val context = MetricsStats.responsesTime().time()
    val context2 = MetricsStats.responsesReadTime().time()
    (QueuesManager.master() ? Size(name)).mapTo[QueueSize].map(r => Ok(Json.obj("name" -> name, "size" -> r.size)))
      .thenCombine { _ =>
        context.close()
        context2.close()
      }
  }

  def clear(name: String) = ApiAction(Constants.adminToken) {
    val context = MetricsStats.responsesTime().time()
    val context2 = MetricsStats.responsesWriteTime().time()
    (QueuesManager.master() ? Clear(name)).mapTo[Cleared].map(_ => Ok).thenCombine { _ =>
      context.close()
      context2.close()
    }
  }

  def poll(name: String) = ApiAction(Constants.token) {
    val context = MetricsStats.responsesTime().time()
    val context2 = MetricsStats.responsesReadTime().time()
    (QueuesManager.master() ? Poll(name)).mapTo[Blob].map(blob => Ok(blob.blob.getOrElse(Json.obj()))).thenCombine { _ =>
      context.close()
      context2.close()
    }
  }

  def create(name: String) = ApiAction(Constants.adminToken) {
    (QueuesManager.master() ? CreateQueue(name)).mapTo[QueueCreated].map(_ => Created)
  }

  def delete(name: String) = ApiAction(Constants.adminToken) {
    (QueuesManager.master() ? DeleteQueue(name)).mapTo[QueueDeleted].map(_ => Ok)
  }
}
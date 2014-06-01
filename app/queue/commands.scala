package queue

import play.api.libs.json.JsObject
import java.io.File

trait QueueCommand
case class Append(name: String, blob: JsObject) extends QueueCommand
case class ReplayAppend(name: String, blob: JsObject) extends QueueCommand
case class Poll(name: String) extends QueueCommand
case class ReplayPoll(name: String) extends QueueCommand
case class Size(name: String) extends QueueCommand
case class Clear(name: String) extends QueueCommand

trait AdminCommand
case class CreateQueue(name: String) extends AdminCommand
case class DeleteQueue(name: String) extends AdminCommand
case class ReplicationCreateQueue(name: String) extends AdminCommand
case class ReplicationDeleteQueue(name: String) extends AdminCommand

trait FileCommand
case class AppendToLog(id: Long, name: String, blob: String) extends FileCommand
case class DeleteFromLog(name: String) extends FileCommand
case class ClearLog(name: String) extends FileCommand

trait Response
case class QueueSize(size: Int) extends Response
case class Blob(blob: Option[JsObject]) extends Response
case class Added(id: Long) extends Response
case class Cleared() extends Response
case class QueueCreated() extends Response
case class QueueDeleted() extends Response


case class SendFilePath()
case class FilePath(path: String)
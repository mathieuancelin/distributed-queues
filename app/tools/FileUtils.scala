package tools

import java.nio.charset.Charset
import java.io.File
import com.google.common.io.{LineProcessor, Files}
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Await}
import queue.{MetricsStats, FilePath, SendFilePath}
import akka.actor.ActorRef
import java.util.concurrent.ConcurrentLinkedQueue

object FileUtils {

  val delimiter = "|||"
  val delimiterSplit = "\\|\\|\\|"
  val APPENDTO = "APPENDTO"
  val DELETEHEAD = "DELETEHEAD"
  val charset = Charset.forName("UTF-8")

  // TODO : handle file rolling
  def emptyFile(file: File) = {
    val context = MetricsStats.diskWriteTime().time()
    if (Constants.persistToDisk) Files.write("", file, charset)
    context.stop()
  }
  // TODO : handle file rolling
  def appendOffer(file: File, name: String, id: Long, blob: String) = {
    val context = MetricsStats.diskWriteTime().time()
    if (Constants.persistToDisk) Files.append(s"$APPENDTO$delimiter$name$delimiter$id$delimiter$blob\n", file, charset)
    context.stop()
  }
  // TODO : handle file rolling
  def appendPoll(file: File, name: String) = {
    val context = MetricsStats.diskWriteTime().time()
    if (Constants.persistToDisk) Files.append(s"$DELETEHEAD$delimiter$name\n", file, charset)
    context.stop()
  }
  // TODO : handle file rolling
  def readLines(file: File, offer: (String, String, String) => Unit, poll: (String) => Unit): Int = {
    val counter = new AtomicInteger(0)
    Files.readLines(file, charset, new LineProcessor[Unit] {
      override def processLine(p1: String): Boolean = {
        p1.split(delimiterSplit).toList match {
          case APPENDTO :: name :: id :: blob :: Nil => {
            offer(name, id, blob)
            counter.incrementAndGet()
            true
          }
          case DELETEHEAD :: name :: Nil => {
            poll(name)
            counter.incrementAndGet()
            true
          }
          case line => true
        }
      }
      override def getResult: Unit = ()
    })
    counter.get()
  }

  // TODO : handle file rolling
  def compressLogFile(diskWriter: ActorRef, name: String, queue: ConcurrentLinkedQueue[String], ec: ExecutionContext) = {
    import akka.pattern.ask
    import collection.JavaConversions._
    // TODO : maybe no such a good idea, lot of trouble here (rolling, etc ...)
    // TODO : avoid blocking here, waste of time ...
    MetricsStats.compactionHits().mark()
    val start = System.currentTimeMillis()
    val path = Await.result(ask(diskWriter, SendFilePath())(Constants.timeout).mapTo[FilePath].map(_.path)(ec), Constants.timeout.duration)
    FileUtils.emptyFile(new File(path))
    queue.foreach(line => FileUtils.appendOffer(new File(path), name, IdGenerator.nextId(), line))
    Constants.logger.info(s"File compression in ${System.currentTimeMillis() - start} ms")
  }
}
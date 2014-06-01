package tools

import java.nio.charset.Charset
import java.io.File
import com.google.common.io.{LineProcessor, Files}
import java.util.concurrent.atomic.AtomicInteger

object FileUtils {

  val delimiter = "|||"
  val delimiterSplit = "\\|\\|\\|"
  val APPENDTO = "APPENDTO"
  val DELETEHEAD = "DELETEHEAD"
  val charset = Charset.forName("UTF-8")

  def emptyFile(file: File) = {
    // TODO : handle non persistent queue
    Files.write("", file, charset)
  }
  def appendOffer(file: File, name: String, id: Long, blob: String) = {
    // TODO : handle non persistent queue
    Files.append(s"$APPENDTO$delimiter$name$delimiter$id$delimiter$blob\n", file, charset)
  }
  def appendPoll(file: File, name: String) = {
    // TODO : handle non persistent queue
    Files.append(s"$DELETEHEAD$delimiter$name\n", file, charset)
  }
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
}
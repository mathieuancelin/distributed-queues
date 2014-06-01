package tools

import play.api.Play
import java.util.concurrent.atomic.AtomicLong

object IdGenerator {

  private[this] val generatorId = Constants.generatorId
  private[this] val minus = 1288834974657L
  private[this] val counter = new AtomicLong(-1L)
  private[this] val lastTimestamp = new AtomicLong(-1L)

  if (generatorId > 1024L) throw new RuntimeException("Generator id can't be larger than 1024")

  def nextId() = synchronized {
    val timestamp = System.currentTimeMillis
    if (timestamp < lastTimestamp.get()) throw new RuntimeException("Clock is running backward. Sorry :-(")
    lastTimestamp.set(timestamp)
    counter.compareAndSet(4095, -1L)
    ((timestamp - minus) << 22L) | (generatorId << 10L) | counter.incrementAndGet()
  }
}

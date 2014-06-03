package queue

import com.codahale.metrics._
import java.util.concurrent.TimeUnit
import tools.Reference
import java.lang.management.{MemoryPoolMXBean, ManagementFactory, RuntimeMXBean}
import com.sun.management.OperatingSystemMXBean
import java.util.Collections
import java.util
import play.api.libs.json.{JsObject, Json}
import scala.collection.JavaConversions._
import play.api.{Mode, Play}

object MetricsStats {

  val metrics = Reference.empty[MetricRegistry]()
  val consoleReporter = Reference.empty[ConsoleReporter]()
  val jmxReporter = Reference.empty[JmxReporter]()

  val compactionHits    = Reference.empty[Meter]()
  val masterHits        = Reference.empty[Meter]()
  val queuesHits        = Reference.empty[Meter]()
  val queuesReadHits    = Reference.empty[Meter]()
  val queuesWriteHits   = Reference.empty[Meter]()
  val responsesTime     = Reference.empty[Timer]()
  val responsesReadTime = Reference.empty[Timer]()
  val responsesWriteTime = Reference.empty[Timer]()
  val routingTime       = Reference.empty[Timer]()
  val diskWriteTime     = Reference.empty[Timer]()

  // TODO : make it cluster wide
  def onStart() = {
    metrics.set(new MetricRegistry())
    metrics().register("memory", MemoryMetrics)
    metrics().register("cpu", CpuMetrics)
    metrics().register("queues", new Gauge[Long] {
      override def getValue: Long = QueuesManager.existing.size()
    })
    metrics().register("total-items", new Gauge[Long] {
      override def getValue: Long = QueuesManager.existing.toList.map { name =>
        Option(FileBackedQueue.queues.get(name))
      }.filter(_.isDefined).map(_.get).map(_.size().toLong).foldLeft(0L)(_ + _)
    })
    compactionHits.set(metrics().meter("compactions.hits"))
    masterHits.set(metrics().meter("master.hits"))
    queuesHits.set(metrics().meter("queues.hits"))
    queuesReadHits.set(metrics().meter("queues.hits.read"))
    queuesWriteHits.set(metrics().meter("queues.hits.write"))
    responsesTime.set(metrics().timer("responses"))
    responsesReadTime.set(metrics().timer("responses.read"))
    responsesWriteTime.set(metrics().timer("responses.write"))
    routingTime.set(metrics().timer("routing"))
    diskWriteTime.set(metrics().timer("disk.write"))

    consoleReporter.set(ConsoleReporter.forRegistry(metrics())
      .convertRatesTo(TimeUnit.SECONDS)
      .convertDurationsTo(TimeUnit.MILLISECONDS)
      .build())

    jmxReporter.set(JmxReporter.forRegistry(metrics()).inDomain("distributed-queues").build())
    jmxReporter().start()

    Play.current.mode match {
      case Mode.Dev => consoleReporter().start(5, TimeUnit.SECONDS)
      case _ =>
    }
  }

  def onStop() = {
    consoleReporter.call(_.stop())
    metrics.cleanup()
  }
}

object MemoryMetrics extends MetricSet {

  private[this] val mxBean = ManagementFactory.getMemoryMXBean
  private[this] val memoryPools = new util.ArrayList[MemoryPoolMXBean](ManagementFactory.getMemoryPoolMXBeans)

  def getMetrics = {
    val gauges = new java.util.HashMap[String, Metric]()
    gauges.put("total.used", new Gauge[Long] {
      def getValue: Long = mxBean.getHeapMemoryUsage.getUsed + mxBean.getNonHeapMemoryUsage.getUsed
    })
    gauges.put("heap.used", new Gauge[Long] {
      def getValue: Long = mxBean.getHeapMemoryUsage.getUsed
    })
    Collections.unmodifiableMap(gauges)
  }
}

object CpuMetrics extends Gauge[Double] {
  def getValue: Double = {
    var retVal: Double = 0.00
    val upTime: Long = runtimeMXBean.getUptime
    val elapsedTime: Long = upTime - lastUpTime
    if (elapsedTime > 0) {
      val cpuTime: Long = osBean.getProcessCpuTime
      retVal = (cpuTime - lastCpuTime) / (elapsedTime * 10000)
      lastUpTime = upTime
      lastCpuTime = cpuTime
    }
    retVal
  }
  private[this] val runtimeMXBean: RuntimeMXBean = ManagementFactory.getRuntimeMXBean
  private[this] val osBean: OperatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean.asInstanceOf[OperatingSystemMXBean]
  private[this] var lastUpTime: Long = runtimeMXBean.getUptime
  private[this] var lastCpuTime: Long = osBean.getProcessCpuTime
}

object JsonReporter {

  private val durationFactor: Double = 1.0 / TimeUnit.MILLISECONDS.toNanos(1);
  private val rateFactor: Double = TimeUnit.SECONDS.toSeconds(1)

  private def getRateUnit = "seconds"
  private def getDurationUnit = "milliseconds"
  private def convertRate(rate: Double): Double = rate * rateFactor
  private def convertDuration(duration: Double): Double = duration * durationFactor

  def toJson(metrics: MetricRegistry): JsObject = {

    val gauges: java.util.SortedMap[String, Gauge[_]] = metrics.getGauges
    val counters: java.util.SortedMap[String, Counter] = metrics.getCounters
    val histograms: java.util.SortedMap[String, Histogram] = metrics.getHistograms
    val meters: java.util.SortedMap[String, Meter] = metrics.getMeters
    val timers: java.util.SortedMap[String, Timer] = metrics.getTimers

    Json.obj(
      "at" -> System.currentTimeMillis(),
      "gauges" -> gauges.toList.map { tuple =>
        tuple._2.getValue match {
          case d: Double => Json.obj("name" -> tuple._1) ++ Json.obj("value" -> d)
          case l: Long => Json.obj("name" -> tuple._1) ++ Json.obj("value" -> l)
          case _ => Json.obj()
        }
      },
      "counters" -> counters.toList.map(tuple => Json.obj("name" -> tuple._1) ++ printCounter(tuple._2)),
      "histograms" -> histograms.toList.map(tuple => Json.obj("name" -> tuple._1) ++ printHistogram(tuple._2)),
      "meters" -> meters.toList.map(tuple => Json.obj("name" -> tuple._1) ++ printMeter(tuple._2)),
      "timers" -> timers.toList.map(tuple => Json.obj("name" -> tuple._1) ++ printTimer(tuple._2))
    )
  }

  private def printCounter(counter: Counter): JsObject = Json.obj("value" -> counter.getCount)

  private def printMeter(meter: Meter): JsObject = {
    Json.obj(
      "unit" -> getRateUnit,
      "count" -> meter.getCount,
      "mean rate" -> convertRate(meter.getMeanRate),
      "1-minute rate" -> convertRate(meter.getOneMinuteRate),
      "5-minute rate" -> convertRate(meter.getFiveMinuteRate),
      "15-minute rate" -> convertRate(meter.getFifteenMinuteRate)
    )
  }

  private def printHistogram(histogram: Histogram): JsObject = {
    val snapshot: Snapshot = histogram.getSnapshot
    Json.obj(
      "count" -> histogram.getCount,
      "min" -> snapshot.getMin,
      "max" -> snapshot.getMax,
      "mean" -> snapshot.getMean,
      "stddev" -> snapshot.getStdDev,
      "median" -> snapshot.getMedian,
      "75%" -> snapshot.get75thPercentile,
      "95%" -> snapshot.get95thPercentile,
      "98%" -> snapshot.get98thPercentile,
      "99%" -> snapshot.get99thPercentile,
      "99.9%" -> snapshot.get999thPercentile
    )
  }

  private def printTimer(timer: Timer): JsObject = {
    val snapshot: Snapshot = timer.getSnapshot
    Json.obj(
      "rateunit" -> getRateUnit,
      "durationunit" -> getDurationUnit,
      "count" -> timer.getCount,
      "mean rate" -> convertRate(timer.getMeanRate),
      "1-minute rate" -> convertRate(timer.getOneMinuteRate),
      "5-minute rate" -> convertRate(timer.getFiveMinuteRate),
      "15-minute rate" -> convertRate(timer.getFifteenMinuteRate),
      "min" -> convertDuration(snapshot.getMin),
      "max" -> convertDuration(snapshot.getMax),
      "mean" -> convertDuration(snapshot.getMean),
      "stddev" -> convertDuration(snapshot.getStdDev),
      "median" -> convertDuration(snapshot.getMedian),
      "75%" -> convertDuration(snapshot.get75thPercentile),
      "95%" -> convertDuration(snapshot.get95thPercentile),
      "98%" -> convertDuration(snapshot.get98thPercentile),
      "99%" -> convertDuration(snapshot.get99thPercentile),
      "99.9%" -> convertDuration(snapshot.get999thPercentile)
    )
  }
}
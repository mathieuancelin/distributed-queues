package queue

import com.codahale.metrics._
import java.util.concurrent.TimeUnit
import tools.Reference
import java.lang.management.{MemoryPoolMXBean, ManagementFactory, RuntimeMXBean}
import com.sun.management.OperatingSystemMXBean
import java.util.Collections
import java.util

object MetricsStats {

  val metrics = new Reference[MetricRegistry]("metrics")
  val consoleReporter = new Reference[ConsoleReporter]("consoleReporter")

  def onStart() = {
    metrics.set(new MetricRegistry())
    metrics().register("jvm.memory", MemoryMetrics)
    metrics().register("cpu", CpuMetrics)
    consoleReporter.set(ConsoleReporter.forRegistry(metrics())
      .convertRatesTo(TimeUnit.SECONDS)
      .convertDurationsTo(TimeUnit.MILLISECONDS)
      .build())

    consoleReporter().start(20, TimeUnit.SECONDS)
  }

  def onStop() = {
    consoleReporter().stop()
    metrics.cleanup()
  }
}

object MemoryMetrics extends MetricSet {

  val mxBean = ManagementFactory.getMemoryMXBean
  val memoryPools = new util.ArrayList[MemoryPoolMXBean](ManagementFactory.getMemoryPoolMXBeans)

  def getMetrics = {
    val gauges = new java.util.HashMap[String, Metric]()
    gauges.put("total.used", new Gauge[Long] {
      def getValue: Long = mxBean.getHeapMemoryUsage.getUsed + mxBean.getNonHeapMemoryUsage.getUsed
    })
    gauges.put("total.committed", new Gauge[Long] {
      def getValue: Long = mxBean.getHeapMemoryUsage.getCommitted + mxBean.getNonHeapMemoryUsage.getCommitted
    })
    gauges.put("heap.used", new Gauge[Long] {
      def getValue: Long = mxBean.getHeapMemoryUsage.getUsed
    })
    gauges.put("heap.committed", new Gauge[Long] {
      def getValue: Long =  mxBean.getHeapMemoryUsage.getCommitted
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
    return retVal
  }
  private final val runtimeMXBean: RuntimeMXBean = ManagementFactory.getRuntimeMXBean
  private final val osBean: OperatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean.asInstanceOf[OperatingSystemMXBean]
  private var lastUpTime: Long = runtimeMXBean.getUptime
  private var lastCpuTime: Long = osBean.getProcessCpuTime
}
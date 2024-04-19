package kyo.scheduler.regulator

import scala.concurrent.duration.*
import scala.annotation.tailrec
import scala.util.hashing.MurmurHash3
import kyo.scheduler.InternalTimer

final class Admission(
    loadAvg: () => Double,
    timer: InternalTimer,
    config: Config =
        Config(
            collectWindowExp = 9, // 2^9=512 ~5 regulate intervals
            collectInterval = 1000.millis,
            collectSamples = 10,
            regulateInterval = 5000.millis,
            jitterUpperThreshold = 100,
            jitterLowerThreshold = 80,
            loadAvgTarget = 0.8,
            stepExp = 1.5
        )
) extends Regulator(loadAvg, timer, config):

    @volatile private var admissionPercent = 100

    protected def probe() =
        val start = System.nanoTime()
        Thread.sleep(1)
        measure(System.nanoTime() - start - 1000000)
    end probe

    protected def update(diff: Int): Unit =
        admissionPercent = Math.max(0, Math.min(100, admissionPercent + diff))

    def reject(keyPath: Seq[String]): Boolean =
        val threshold = admissionPercent / keyPath.length
        @tailrec
        def loop(keys: Seq[String], index: Int): Boolean =
            keys match
                case Seq() => false
                case key +: rest =>
                    val hash           = MurmurHash3.stringHash(key)
                    val layerThreshold = threshold * (index + 1)
                    if (hash.abs % 100) <= (layerThreshold * 100) then
                        loop(rest, index + 1)
                    else
                        true
                    end if
        loop(keyPath, 0)
    end reject

    override def toString = s"Admission($admissionPercent)"

end Admission

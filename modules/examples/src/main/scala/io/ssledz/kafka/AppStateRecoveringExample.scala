package io.ssledz.kafka

import cats.effect.concurrent.Ref
import cats.effect.{ExitCode, IO}
import io.chrisdavenport.log4cats.Logger
import io.ssledz.kafka.IOKafkaApp.printlnConsole
import io.ssledz.kafka.consumer.KafkaConsumer.OffsetRange
import io.ssledz.kafka.consumer.{KafkaConsumerDriver, KafkaConsumerProcessor}
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition

object AppStateRecoveringExample extends IOKafkaApp {

  def run(args: List[String]): IO[ExitCode] =
    KafkaConsumerDriver.newDriverResource[IO](cfg, LogErrorHandler).use { driver =>
      for {
        _            <- Logger[IO].info("starting...")
        refState     <- Ref.of[IO, AppState](Init)
        c            <- driver.newConsumer(KafkaConsumerProcessor.recordSyncConsumer(consoleConsumer(refState)))
        rangeOffsets <- c.rangeOffsets
        _            <- refState.set(Recovering.from(rangeOffsets))
        state        <- refState.get
        _            <- Logger[IO].info(s"state: $state")
        _            <- c.pollForAssignment
        _            <- c.seekToBeginning
        _            <- c.start
        _            <- IO.never
      } yield ExitCode.Success
    }

  def consoleConsumer(refState: Ref[IO, AppState])(record: ConsumerRecord[String, String]): IO[Unit] =
    for {
      _ <- printlnConsole(s"Consuming: ${record.key()}:${record.value()}\t(t: ${record.topic()}, p: ${record
        .partition()}, offset: ${record.offset()})")
      stateBefore <- refState.get
      _           <- Logger[IO].info(s"state before: $stateBefore")
      _           <- refState.update(state => state.update(record.topic(), record.partition(), record.offset()))
      state       <- refState.get
      _           <- Logger[IO].info(s"state after: $state")
    } yield ()

  sealed trait AppState {
    def update(topic: String, partition: Int, offset: Long): AppState = this match {
      case r @ Recovering(offsets, start) =>
        val ret = offsets.updatedWith(topic) {
          case Some(p2o) =>
            val ret = p2o.updatedWith(partition) {
              case v @ Some(endOffset) if endOffset > offset + 1 => v
              case _                                             => None
            }
            if (ret.isEmpty) None else Some(ret)
          case None => None
        }
        if (ret.isEmpty) Recovered(start, System.currentTimeMillis()) else r.copy(offsets = ret)
      case _ => this
    }
  }

  case object Init extends AppState

  case class Recovering(offsets: Map[String, Map[Int, Long]], start: Long) extends AppState

  object Recovering {
    def from(offsets: Map[TopicPartition, OffsetRange]): Recovering = {
      val off = offsets
        .filterNot { case (_, or) => or.start == or.end }
        .groupBy { case (tp, _) => tp.topic() }
        .map { case (t, off) => (t, off.map { case (tp, or) => (tp.partition(), or.end) }) }
      Recovering(off, System.currentTimeMillis())
    }
  }

  case class Recovered(start: Long, end: Long) extends AppState

}

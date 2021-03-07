package io.ssledz.kafka

import cats.effect.{ExitCode, IO}
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import io.ssledz.kafka.consumer.{KafkaConsumerDriver, KafkaConsumerProcessor}

import scala.concurrent.duration._

object GetConsumerOffsetExample extends IOKafkaApp {

  def run(args: List[String]): IO[ExitCode] =
    KafkaConsumerDriver.newDriverResource[IO](cfg, LogErrorHandler).use { driver =>
      for {
        _         <- Logger[IO].info("starting...")
        c         <- driver.newConsumer(KafkaConsumerProcessor.recordSyncConsumer(IOKafkaApp.consoleConsumer))
        _         <- c.pollForAssignment
        _         <- c.seekToBeginning
        committed <- c.committed
        _         <- Logger[IO].info(s"committed: $committed")
        _         <- c.seekToBeginning
        eos       <- c.endOffsets
        _         <- Logger[IO].info(s"end of offsets: $eos")
        _         <- c.start
        _ <- (c.beginningOffsets.flatMap(bos => Logger[IO].info(s"begin of offsets: $bos")) *> IO.sleep(
          4000.millis
        )).foreverM.start
        _ <- (c.endOffsets.flatMap(eos => Logger[IO].info(s"end of offsets: $eos")) *> IO.sleep(
          3000.millis
        )).foreverM.start
        _ <- (c.rangeOffsets.flatMap(ros => Logger[IO].info(s"range of offsets: $ros")) *> IO.sleep(
          3000.millis
        )).foreverM.start
        _ <- IO.never
      } yield ExitCode.Success
    }
}

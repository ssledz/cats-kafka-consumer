package io.ssledz.kafka

import cats.effect.{ExitCode, IO}
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import io.ssledz.kafka.IOKafkaApp.pollForAssignment
import io.ssledz.kafka.consumer.{KafkaConsumerDriver, KafkaConsumerProcessor}

import scala.concurrent.duration._

object SimpleSerialConsumer extends IOKafkaApp {

  def run(args: List[String]): IO[ExitCode] =
    KafkaConsumerDriver.newDriverResource[IO](cfg, LogErrorHandler).use { driver =>
      for {
        _         <- Logger[IO].info("starting...")
        c         <- driver.newConsumer(KafkaConsumerProcessor.recordSyncConsumer(IOKafkaApp.consoleConsumer))
        assigment <- pollForAssignment(c)
        _         <- Logger[IO].info(s"assigment: $assigment")
        _         <- Logger[IO].info("seek to the beginning")
        _         <- c.seekToBeginning
        committed <- c.committed
        _         <- Logger[IO].info(s"committed after reset: $committed")
        _         <- c.start
        _ <- (c.committed.flatMap(committed => Logger[IO].info(s"committed: $committed")) *> IO.sleep(
          1000.millis
        )).foreverM.start
        _ <- (c.seekToBeginning *> Logger[IO].info("seekToBeginning") *> IO.sleep(5000.millis)).foreverM.start
        _ <- IO.never
      } yield ExitCode.Success
    }

}

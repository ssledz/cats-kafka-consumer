package io.ssledz.kafka

import cats.effect.{IO, IOApp}
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.ssledz.kafka.consumer.config.{KafkaConnectionConfig, KafkaConsumerConfig}
import io.ssledz.kafka.consumer.{ConsumingError, ConsumingErrorHandler, KafkaConsumerApi}
import org.apache.kafka.clients.consumer.{Consumer, ConsumerRecord, KafkaConsumer}
import org.apache.kafka.common.TopicPartition

import java.time.Duration
import java.util.Properties
import java.{util => ju}

trait IOKafkaApp extends IOApp {

  implicit def unsafeLogger: Logger[IO] = Slf4jLogger.getLogger[IO]

  def cfg: KafkaConsumerConfig = IOKafkaApp.DefaultConfig

  implicit def consumerFactory(props: Properties): Consumer[String, String] = new KafkaConsumer(props)

  object LogErrorHandler extends ConsumingErrorHandler[IO] {
    def handleErrors(errors: List[ConsumingError]): IO[Unit] =
      Logger[IO].error(s"Error during consuming records $errors")
  }

}

object IOKafkaApp {

  val DefaultConfig: KafkaConsumerConfig = KafkaConsumerConfig(
    connection = KafkaConnectionConfig(List("localhost:9092")),
    groupId = "my-test-group",
    topics = List("test-kafka-consumer"),
    clientId = "my-test-client"
  )

  def printlnConsole(message: String): IO[Unit] = IO(println(s"[${Thread.currentThread().getName}] " + message))

  def pollForAssignment(c: KafkaConsumerApi[IO]): IO[ju.Set[TopicPartition]] =
    for {
      assigment <- c.assignment
      _         <- if (assigment.isEmpty) c.poll(Duration.ofMillis(1000)) *> pollForAssignment(c) else IO.unit
    } yield assigment

  def consoleConsumer(record: ConsumerRecord[String, String]): IO[Unit] =
    for {
      _ <- printlnConsole(s"Consuming: ${record.key()}:${record.value()}")
    } yield ()
}

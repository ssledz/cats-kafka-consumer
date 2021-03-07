package io.ssledz.kafka.consumer

import cats.effect.concurrent.{MVar, Ref}
import cats.effect.{IO, Timer}
import cats.implicits.{catsSyntaxEq => _, _}
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.ssledz.kafka.consumer.KafkaConsumerDriverTest._
import io.ssledz.suite.KafkaConsumerMockSuit.KafkaProducerMock
import io.ssledz.suite.{IOAssertion, KafkaConsumerMockSuit}
import org.apache.kafka.clients.consumer.Consumer

import java.util.Properties
import scala.concurrent.duration._

class KafkaConsumerDriverTest extends KafkaConsumerMockSuit {

  implicit def unsafeLogger: Logger[IO] = Slf4jLogger.getLogger[IO]

  test("start new consumer ready for consuming records2") {
    withResources { case (consumer, producer) =>
      IOAssertion {
        implicit val consumerFactory: Properties => Consumer[String, String] = _ => consumer
        for {
          driver <- KafkaConsumerDriver.newDriver[IO](TestConfig, ConsumingErrorHandler.noOp)
          _      <- Logger[IO].debug("Driver created")
          ref    <- Ref.of[IO, List[TestRecord]](List.empty[TestRecord])
          flag   <- MVar.empty[IO, Unit]
          _ <- driver
            .newConsumer(KafkaConsumerProcessor.valueSyncConsumer(x => ref.update(TestRecord("consumer1", x) :: _)))
            .flatMap(_.start)
          _ <- driver
            .newConsumer(KafkaConsumerProcessor.valueSyncConsumer(x => ref.update(TestRecord("consumer2", x) :: _)))
            .flatMap(_.start)
          _               <- Logger[IO].debug("starting feeding")
          _               <- feeder(producer, NumberOfRecordsToProduce).start
          _               <- checker(NumberOfRecordsToProduce, ref, flag, NumberOfRecordsToProduce).start
          _               <- flag.take
          _               <- driver.stop
          consumedRecords <- ref.get
        } yield {
          val consumedRecordsPerConsumer = consumedRecords.groupBy(_.consumerName).mapValues(_.size)
          assert(consumedRecords.size === NumberOfRecordsToProduce)
          assert(consumedRecordsPerConsumer.size === 2)
        }
      }
    }
  }

  def feeder(kafkaProducer: KafkaProducerMock, cnt: Int): IO[Unit] =
    (1 to cnt).toList
      .traverse(i =>
        Timer[IO].sleep(10.millis) *> IO(kafkaProducer.addRecord("key" + i, "record" + i)) *> Logger[IO]
          .debug(s"feeding $i record")
      )
      .void

  def checker(cnt: Int, ref: Ref[IO, List[TestRecord]], flag: MVar[IO, Unit], it: Int): IO[Unit] =
    for {
      xs <- ref.get
      _ <-
        if (xs.size == cnt || it == 0) flag.put(()) else Timer[IO].sleep(100.millis) *> checker(cnt, ref, flag, it - 1)
    } yield ()

}

object KafkaConsumerDriverTest {

  private val NumberOfRecordsToProduce: Int = 20

  case class TestRecord(consumerName: String, value: String)

}

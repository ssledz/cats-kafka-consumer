package io.ssledz.suite

import cats.effect.{IO, Resource}
import io.ssledz.kafka.consumer.config.KafkaConsumerConfig.KafkaPollConfig
import io.ssledz.kafka.consumer.config.{KafkaConnectionConfig, KafkaConsumerConfig}
import io.ssledz.suite.KafkaConsumerMockSuit.{KafkaClients, KafkaProducerMock}
import org.apache.kafka.clients.consumer.{Consumer, ConsumerRecord, MockConsumer, OffsetResetStrategy}
import org.apache.kafka.common.TopicPartition

import java.util.concurrent.atomic.AtomicInteger
import scala.jdk.CollectionConverters._

trait KafkaConsumerMockSuit extends ResourceSuite[KafkaClients] {

  val TestTopic: String = "test-topic"

  val TestConfig: KafkaConsumerConfig =
    KafkaConsumerConfig(
      KafkaConnectionConfig(List.empty),
      "test-group",
      List(TestTopic),
      "test-client",
      KafkaPollConfig(1000)
    )

  def resources: Resource[IO, KafkaClients] = {
    val kafkaConsumer = newMockConsumer(TestTopic)
    val kafkaProducer = new KafkaProducerMock(kafkaConsumer, TestTopic)
    Resource.liftF(IO.pure(kafkaConsumer -> kafkaProducer))
  }

  def newMockConsumer(topic: String): MockConsumer[String, String] = {

    val consumer = new MockConsumer[String, String](OffsetResetStrategy.LATEST)

    consumer.subscribe(List(topic).asJava)

    val tp = new TopicPartition(topic, 0)

    consumer.rebalance(List(tp).asJava)

    consumer.updateEndOffsets(Map(tp -> Long.box(0L)).asJava)

    consumer
  }

}

object KafkaConsumerMockSuit {

  type KafkaClients = (Consumer[String, String], KafkaProducerMock)

  class KafkaProducerMock(consumer: MockConsumer[String, String], topic: String) {
    private val offset: AtomicInteger = new AtomicInteger(0)

    def addRecord(key: String, record: String, partition: Int = 0): Unit =
      consumer.addRecord(
        new ConsumerRecord[String, String](topic, partition, offset.getAndIncrement().toLong, key, record)
      )
  }

}

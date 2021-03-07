package io.ssledz.kafka.consumer.config

import cats.Show

case class KafkaConnectionConfig(brokers: List[String], jaasCredentials: Option[String] = None)

object KafkaConnectionConfig {

  implicit val showInstance: Show[KafkaConnectionConfig] = Show.show(c => s"""
      |brokers:          : ${c.brokers}
      |jaasCredentials   : ${c.jaasCredentials.getOrElse("none")}
      |""".stripMargin)

}

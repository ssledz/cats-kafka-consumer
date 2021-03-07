package io.ssledz.kafka.consumer.config

import cats.Show
import io.ssledz.kafka.consumer.config.KafkaConsumerConfig.KafkaPollConfig
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.apache.kafka.common.serialization.StringDeserializer

import java.util.Properties

case class KafkaConsumerConfig(
    connection: KafkaConnectionConfig,
    groupId: String,
    topics: List[String],
    clientId: String,
    poll: KafkaPollConfig = KafkaPollConfig(),
    maxPartitionFetchBytes: Int = ConsumerConfig.DEFAULT_MAX_PARTITION_FETCH_BYTES,
    fetchMaxBytes: Int = ConsumerConfig.DEFAULT_FETCH_MAX_BYTES
)

object KafkaConsumerConfig {

  /** The maximum delay between invocations of poll() in ms */
  private final val MaxPollInterval = 3000

  private final val DefaultPollInterval = 1000

  case class KafkaPollConfig(
      interval: Long = DefaultPollInterval,
      maxInterval: Int = MaxPollInterval,
      maxRecords: Option[Int] = None
  ) {
    assert(interval < maxInterval)
  }

  def propsFrom(config: KafkaConsumerConfig, cnt: Int): Properties = {
    val ps = new Properties

    ps.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, config.connection.brokers.mkString(","))
    ps.put(CommonClientConfigs.CLIENT_ID_CONFIG, config.clientId + s"-$cnt")

    config.connection.jaasCredentials.foreach { cred =>
      ps.put(SaslConfigs.SASL_JAAS_CONFIG, cred)
      ps.put(SaslConfigs.SASL_MECHANISM, "PLAIN")
      ps.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, SecurityProtocol.SASL_PLAINTEXT.name)
    }

    ps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, classOf[StringDeserializer].getName)
    ps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, classOf[StringDeserializer].getName)
    ps.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, int2Integer(config.maxPartitionFetchBytes))
    ps.put(ConsumerConfig.FETCH_MAX_BYTES_CONFIG, int2Integer(config.fetchMaxBytes))
    ps.put(ConsumerConfig.GROUP_ID_CONFIG, config.groupId)
    ps.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, int2Integer(config.poll.maxInterval))
    config.poll.maxRecords.foreach(v => ps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, int2Integer(v)))

    ps

  }

  implicit val showInstance: Show[KafkaConsumerConfig] = Show.show(c => s"""
       | consumer.${c.clientId}.brokers                     : ${c.connection.brokers}
       | consumer.${c.clientId}.jaasCredentials             : ${c.connection.jaasCredentials.getOrElse("none")}
       | consumer.${c.clientId}.groupId                     : ${c.groupId}
       | consumer.${c.clientId}.topics                      : ${c.topics.mkString("[", ",", "]")}
       | consumer.${c.clientId}.poll.maxInterval            : ${c.poll.maxInterval}
       | consumer.${c.clientId}.poll.interval               : ${c.poll.interval}
       | consumer.${c.clientId}.poll.maxRecords             : ${c.poll.maxRecords.getOrElse("none")}
       | consumer.${c.clientId}.poll.maxPartitionFetchBytes : ${c.maxPartitionFetchBytes}
       | consumer.${c.clientId}.poll.fetchMaxBytes          : ${c.fetchMaxBytes}
       |""".stripMargin)

}

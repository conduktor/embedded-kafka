package io.github.embeddedkafka

trait EmbeddedKafkaConfig {
  def kafkaPort: Int

  def kafkaSslPort: Option[Int]

  def kafkaSaslPlainTextPort: Option[Int]

  def kafkaSaslSslPort: Option[Int]

  def zooKeeperPort: Int

  def customBrokerProperties: Map[String, String]

  def customProducerProperties: Map[String, String]

  def customConsumerProperties: Map[String, String]

  def numberOfThreads: Int
}

case class EmbeddedKafkaConfigImpl(
    kafkaPort: Int,
    zooKeeperPort: Int,
    kafkaSslPort: Option[Int],
    kafkaSaslPlainTextPort: Option[Int],
    kafkaSaslSslPort: Option[Int],
    customBrokerProperties: Map[String, String],
    customProducerProperties: Map[String, String],
    customConsumerProperties: Map[String, String]
) extends EmbeddedKafkaConfig {
  override val numberOfThreads: Int = 2
}

object EmbeddedKafkaConfig {
  lazy val defaultKafkaPort     = 6001
  lazy val defaultZookeeperPort = 6000

  implicit val defaultConfig: EmbeddedKafkaConfig = apply()

  def apply(
      kafkaPort: Int = defaultKafkaPort,
      zooKeeperPort: Int = defaultZookeeperPort,
      kafkaSslPort: Option[Int] = None,
      kafkaSaslPlainTextPort: Option[Int] = None,
      kafkaSaslSslPort: Option[Int] = None,
      customBrokerProperties: Map[String, String] = Map.empty,
      customProducerProperties: Map[String, String] = Map.empty,
      customConsumerProperties: Map[String, String] = Map.empty
  ): EmbeddedKafkaConfig =
    EmbeddedKafkaConfigImpl(
      kafkaPort = kafkaPort,
      zooKeeperPort = zooKeeperPort,
      kafkaSslPort = kafkaSslPort,
      kafkaSaslPlainTextPort = kafkaSaslPlainTextPort,
      kafkaSaslSslPort = kafkaSaslSslPort,
      customBrokerProperties = customBrokerProperties,
      customProducerProperties = customProducerProperties,
      customConsumerProperties = customConsumerProperties
    )
}

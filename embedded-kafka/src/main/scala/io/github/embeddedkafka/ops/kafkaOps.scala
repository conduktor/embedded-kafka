package io.github.embeddedkafka.ops

import java.nio.file.Path

import kafka.server.{KafkaConfig, KafkaServer}
import io.github.embeddedkafka.{
  EmbeddedK,
  EmbeddedKafkaConfig,
  EmbeddedServer,
  EmbeddedZ
}
import org.apache.kafka.common.security.auth.SecurityProtocol

import scala.jdk.CollectionConverters._

/**
  * Trait for Kafka-related actions.
  */
trait KafkaOps {
  protected val brokerId: Short                 = 0
  protected val autoCreateTopics: Boolean       = true
  protected val logCleanerDedupeBufferSize: Int = 1048577

  private[embeddedkafka] def startKafka(
      kafkaPort: Int,
      zooKeeperPort: Int,
      kafkaSslPort: Option[Int],
      kafkaSaslPlainTextPort: Option[Int],
      kafkaSaslSslPort: Option[Int],
      customBrokerProperties: Map[String, String],
      kafkaLogDir: Path
  ) = {
    val zkAddress = s"localhost:$zooKeeperPort"
    val listener = Map(
      SecurityProtocol.PLAINTEXT      -> Some(kafkaPort),
      SecurityProtocol.SASL_SSL       -> kafkaSaslSslPort,
      SecurityProtocol.SASL_PLAINTEXT -> kafkaSaslPlainTextPort,
      SecurityProtocol.SSL            -> kafkaSslPort
    ).flatMap {
      case (protocol, Some(port)) =>
        Some(s"$protocol://localhost:$port")
      case (_, None) =>
        None
    }.mkString(",")

    val brokerProperties = Map[String, Object](
      KafkaConfig.ZkConnectProp              -> zkAddress,
      KafkaConfig.BrokerIdProp               -> brokerId.toString,
      KafkaConfig.ListenersProp              -> listener,
      KafkaConfig.AdvertisedListenersProp    -> listener,
      KafkaConfig.AutoCreateTopicsEnableProp -> autoCreateTopics.toString,
      KafkaConfig.LogDirProp -> kafkaLogDir.toAbsolutePath.toString,
      KafkaConfig.LogFlushIntervalMessagesProp           -> 1.toString,
      KafkaConfig.OffsetsTopicReplicationFactorProp      -> 1.toString,
      KafkaConfig.OffsetsTopicPartitionsProp             -> 1.toString,
      KafkaConfig.TransactionsTopicReplicationFactorProp -> 1.toString,
      KafkaConfig.TransactionsTopicMinISRProp            -> 1.toString,
      // The total memory used for log deduplication across all cleaner threads, keep it small to not exhaust suite memory
      KafkaConfig.LogCleanerDedupeBufferSizeProp -> logCleanerDedupeBufferSize.toString
    ) ++ customBrokerProperties

    val broker = new KafkaServer(new KafkaConfig(brokerProperties.asJava))
    broker.startup()
    broker
  }

}

/**
  * [[KafkaOps]] extension relying on `RunningServersOps` for keeping track of
  * running [[EmbeddedK]] instances.
  */
trait RunningKafkaOps {
  this: KafkaOps with RunningServersOps =>

  /**
    * Starts a Kafka broker in memory, storing logs in a specific location.
    *
    * @param kafkaLogsDir
    *   the path for the Kafka logs
    * @param factory
    *   an [[EmbeddedZ]] server
    * @param config
    *   an implicit [[EmbeddedKafkaConfig]]
    * @return
    *   an [[EmbeddedK]] server
    */
  def startKafka(kafkaLogsDir: Path, factory: Option[EmbeddedZ] = None)(
      implicit config: EmbeddedKafkaConfig
  ): EmbeddedK = {
    val kafkaServer = startKafka(
      kafkaPort = config.kafkaPort,
      kafkaSslPort = config.kafkaSslPort,
      kafkaSaslPlainTextPort = config.kafkaSaslPlainTextPort,
      kafkaSaslSslPort = config.kafkaSaslSslPort,
      zooKeeperPort = config.zooKeeperPort,
      customBrokerProperties = config.customBrokerProperties,
      kafkaLogDir = kafkaLogsDir
    )

    val listenedPort = kafkaPort(kafkaServer)
    val configWithUsedPorts = EmbeddedKafkaConfig(
      kafkaPort = listenedPort(SecurityProtocol.PLAINTEXT),
      kafkaSslPort = listenedPort.get(SecurityProtocol.SSL),
      kafkaSaslSslPort = listenedPort.get(SecurityProtocol.SASL_SSL),
      kafkaSaslPlainTextPort =
        listenedPort.get(SecurityProtocol.SASL_PLAINTEXT),
      zooKeeperPort = config.zooKeeperPort,
      customBrokerProperties = config.customBrokerProperties,
      customConsumerProperties = config.customProducerProperties,
      customProducerProperties = config.customConsumerProperties
    )

    val broker =
      EmbeddedK(factory, kafkaServer, kafkaLogsDir, configWithUsedPorts)
    runningServers.add(broker)
    broker
  }

  /**
    * Stops all in memory Kafka instances, preserving the logs directories.
    */
  def stopKafka(): Unit =
    runningServers.stopAndRemove(isEmbeddedK, clearLogs = false)

  private[embeddedkafka] def isEmbeddedK(server: EmbeddedServer): Boolean =
    server.isInstanceOf[EmbeddedK]

  private[embeddedkafka] def kafkaPort(
      kafkaServer: KafkaServer
  ): Map[SecurityProtocol, Int] = {
    kafkaServer.config.listeners.map { endpoint =>
      endpoint.securityProtocol -> kafkaServer.boundPort(endpoint.listenerName)
    }.toMap
  }
}

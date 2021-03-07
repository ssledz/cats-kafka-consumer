package io.ssledz.kafka.consumer

import cats.effect.concurrent.{MVar, Ref}
import cats.effect.implicits._
import cats.effect.{ContextShift, Resource, Sync, _}
import cats.implicits._
import cats.{Monad, MonadError, Parallel}
import io.chrisdavenport.log4cats.Logger
import io.ssledz.kafka.consumer.KafkaConsumer.{topicPartitionFrom, OffsetRange}
import io.ssledz.kafka.consumer.KafkaConsumerDriver.ConsumerFactory
import io.ssledz.kafka.consumer.ThreadResources.KafkaBlocker
import io.ssledz.kafka.consumer.config.KafkaConsumerConfig
import org.apache.kafka.clients.consumer.{Consumer, ConsumerRebalanceListener, ConsumerRecord, OffsetAndMetadata}
import org.apache.kafka.common.errors.WakeupException
import org.apache.kafka.common.{PartitionInfo, TopicPartition}

import java.time.Duration
import java.util.Properties
import java.{lang => jl, util => ju}
import scala.jdk.CollectionConverters._

trait KafkaConsumerDriverApi[F[_]] {

  def newConsumer(processor: KafkaConsumerProcessor[F]): F[KafkaConsumerApi[F]]

  def stop: F[Unit]
}

trait KafkaConsumerApi[F[_]] {

  def seekToBeginning: F[Unit]

  def committed(tps: ju.Set[TopicPartition]): F[Map[TopicPartition, OffsetAndMetadata]]

  def committed: F[Map[TopicPartition, OffsetAndMetadata]]

  def assignment: F[ju.Set[TopicPartition]]

  def endOffsets(topic: ju.Collection[TopicPartition]): F[Map[TopicPartition, jl.Long]]

  def partitionFor(topic: String): F[List[PartitionInfo]]

  def wakeup: F[Unit]

  def start: F[Unit]

  def poll(timeout: Duration): F[List[ConsumerRecord[String, String]]]

  def endOffsets: F[Map[TopicPartition, jl.Long]]

  def beginningOffsets: F[Map[TopicPartition, jl.Long]]

  def rangeOffsets: F[Map[TopicPartition, OffsetRange]]

  def pollForAssignment: F[ju.Set[TopicPartition]]
}

private class KafkaConsumerApiImpl[F[_]: Async: ContextShift: BracketThrow: Logger](
    consumer: Consumer[String, String],
    blocker: KafkaBlocker,
    val startFlag: MVar[F, Unit],
    lock: MLock[F]
) extends KafkaConsumerApi[F]
    with ConsumerRebalanceListener {

  private val startedRef = Ref.unsafe(false)

  def start: F[Unit] = lock.greenLight(startFlag.put(()) *> startedRef.set(true))

  def unsafeSeekToBeginning: F[Unit] =
    for {
      tps <- unsafeAssignment
      _   <- Logger[F].debug(s"Seeking to the beginning for $tps")
      _   <- Sync[F].delay(consumer.seekToBeginning(tps))
    } yield ()

  def seekToBeginning: F[Unit] = safeRun(unsafeSeekToBeginning)

  private def safeRun[A](fa: F[A]): F[A] =
    for {
      started <- startedRef.get
      a       <- if (started) blocker.underlying.blockOn(fa) else lock.greenLight(fa)
    } yield a

  def unsafeSubscription: F[ju.Set[String]] = Sync[F].delay(consumer.subscription)

  def unsafePartitionFor(topic: String): F[List[PartitionInfo]] =
    Sync[F].delay(consumer.partitionsFor(topic).asScala.toList)

  def unsafePartitions: F[List[PartitionInfo]] =
    for {
      sub <- unsafeSubscription
      ps  <- sub.asScala.toList.traverse(unsafePartitionFor)
    } yield ps.flatten

  def partitionFor(topic: String): F[List[PartitionInfo]] = safeRun(unsafePartitionFor(topic))

  def unsafeEndOffsets(topics: ju.Collection[TopicPartition]): F[Map[TopicPartition, jl.Long]] =
    Sync[F].delay(consumer.endOffsets(topics).asScala.toMap)

  def endOffsets(topics: ju.Collection[TopicPartition]): F[Map[TopicPartition, jl.Long]] = safeRun(
    unsafeEndOffsets(topics)
  )

  def unsafeBeginningOffsets(topics: ju.Collection[TopicPartition]): F[Map[TopicPartition, jl.Long]] =
    Sync[F].delay(consumer.beginningOffsets(topics).asScala.toMap)

  def beginningOffsets(topics: ju.Collection[TopicPartition]): F[Map[TopicPartition, jl.Long]] = safeRun(
    unsafeBeginningOffsets(topics)
  )

  def unsafeBeginningOffsets: F[Map[TopicPartition, jl.Long]] =
    for {
      partitions <- unsafePartitions
      bos        <- unsafeBeginningOffsets(topicPartitionFrom(partitions).toSet.asJava)
    } yield bos

  def unsafePollForAssignment: F[ju.Set[TopicPartition]] =
    for {
      assignment <- unsafeAssignment
      newAssignment <-
        if (assignment.isEmpty) unsafePoll(Duration.ofMillis(100)) *> unsafePollForAssignment
        else Sync[F].pure(assignment)
    } yield newAssignment

  def pollForAssignment: F[ju.Set[TopicPartition]] = safeRun(unsafePollForAssignment)

  def beginningOffsets: F[Map[TopicPartition, jl.Long]] = safeRun(unsafeBeginningOffsets)

  def unsafeEndOffsets: F[Map[TopicPartition, jl.Long]] =
    for {
      partitions <- unsafePartitions
      eos        <- unsafeEndOffsets(topicPartitionFrom(partitions).toSet.asJava)
    } yield eos

  def endOffsets: F[Map[TopicPartition, jl.Long]] = safeRun(unsafeEndOffsets)

  def rangeOffsets: F[Map[TopicPartition, OffsetRange]] = safeRun(unsafeRangeOffsets)

  def unsafeRangeOffsets: F[Map[TopicPartition, OffsetRange]] =
    for {
      partitions <- unsafePartitions
      tps = topicPartitionFrom(partitions).toSet.asJava
      begin <- unsafeBeginningOffsets(tps)
      end   <- unsafeEndOffsets(tps)
    } yield begin.map { case (tp, start) => (tp, OffsetRange(start, end.get(tp).map(_.toLong).getOrElse(start))) }

  def unsafeAssignment: F[ju.Set[TopicPartition]] = Sync[F].delay(consumer.assignment())

  def assignment: F[ju.Set[TopicPartition]] = safeRun(unsafeAssignment)

  def unsafeCommitted(tps: ju.Set[TopicPartition]): F[Map[TopicPartition, OffsetAndMetadata]] =
    Sync[F].delay(consumer.committed(tps).asScala.toMap)

  def committed(tps: ju.Set[TopicPartition]): F[Map[TopicPartition, OffsetAndMetadata]] = safeRun(unsafeCommitted(tps))

  def unsafeCommitted: F[Map[TopicPartition, OffsetAndMetadata]] =
    for {
      partitions <- unsafePartitions
      committed  <- unsafeCommitted(topicPartitionFrom(partitions).toSet.asJava)
    } yield committed

  def committed: F[Map[TopicPartition, OffsetAndMetadata]] = safeRun(unsafeCommitted)

  def unsafePoll(timeout: Duration): F[List[ConsumerRecord[String, String]]] =
    Sync[F].delay(consumer.poll(timeout).asScala.toList)

  def poll(timeout: Duration): F[List[ConsumerRecord[String, String]]] = safeRun(unsafePoll(timeout))

  def close: F[Unit] = Sync[F].delay(consumer.close())

  def wakeup: F[Unit] = Sync[F].delay(consumer.wakeup())

  def onPartitionsRevoked(partitions: ju.Collection[TopicPartition]): Unit = ()

  def onPartitionsAssigned(partitions: ju.Collection[TopicPartition]): Unit = ()
}

private class KafkaConsumerDriver[F[_]: Concurrent: ContextShift: Parallel: Logger](
    config: KafkaConsumerConfig,
    errorHandler: ConsumingErrorHandler[F],
    consumerFactory: ConsumerFactory
) extends KafkaConsumerDriverApi[F] {

  private val consumersRef = Ref.unsafe(List.empty[KafkaConsumerApiImpl[F]])

  private def pollLoop(
      processor: KafkaConsumerProcessor[F]
  )(implicit blocker: KafkaBlocker, consumer: KafkaConsumerApiImpl[F]): F[Unit] = {
    def onRecords(records: List[ConsumerRecord[String, String]]): F[Unit] =
      for {
        result <- processor.consume(records).attempt.map {
          case Left(err)     => List(Left(ConsumingRecordsError(records, err)))
          case Right(values) => values
        }
        (errors, _) = result.partition(_.isLeft)
        _ <- if (errors.isEmpty) Monad[F].unit else errorHandler.handleErrors(errors.flatMap(_.left.toOption))
      } yield ()

    for {
      records <- blocker.underlying.blockOn(consumer.unsafePoll(Duration.ofMillis(config.poll.interval)))
      _       <- if (records.isEmpty) Monad[F].unit else onRecords(records)
    } yield ()
  }

  private def newConsumer(implicit blocker: KafkaBlocker): F[KafkaConsumerApiImpl[F]] =
    for {
      startFlag <- MVar.empty[F, Unit]
      lock      <- MLock[F]
      cnt       <- consumersRef.get.map(_.size)
      kc <- Sync[F].delay {
        val consumer = consumerFactory(KafkaConsumerConfig.propsFrom(config, cnt))
        val kc       = new KafkaConsumerApiImpl(consumer, blocker, startFlag, lock)
        consumer.subscribe(config.topics.asJava, kc)
        kc
      }
    } yield kc

  private def kafka(implicit blocker: KafkaBlocker): Resource[F, KafkaConsumerApiImpl[F]] =
    Resource.make(newConsumer)(c => blocker.underlying.blockOn(c.close)(ContextShift[F]))

  def stop: F[Unit] =
    for {
      cs <- consumersRef.get
      _  <- Logger[F].debug(s"Stopping consumers (${cs.size})")
      _  <- cs.traverse(c => c.wakeup)
    } yield ()

  private def consumerFrom(processor: KafkaConsumerProcessor[F], box: MVar[F, KafkaConsumerApi[F]]): F[Unit] =
    ThreadResources.kafka(config.clientId).use { implicit kafkaBlocker =>
      kafka.use { implicit consumer =>
        for {
          _ <- consumersRef.update(consumer :: _)
          _ <- box.put(consumer)
          _ <- consumer.startFlag.take
          _ <- pollLoop(processor).foreverM.void.attempt.flatMap {
            case Left(err) if err.isInstanceOf[WakeupException] => Sync[F].unit
            case Left(err)                                      => Sync[F].raiseError(err).void
            case _                                              => Sync[F].unit
          }
        } yield ()
      }
    }

  def newConsumer(processor: KafkaConsumerProcessor[F]): F[KafkaConsumerApi[F]] =
    for {
      box      <- MVar.empty[F, KafkaConsumerApi[F]]
      _        <- consumerFrom(processor, box).start
      consumer <- box.take
    } yield consumer

}

object KafkaConsumerDriver {

  type ConsumerFactory = Properties => Consumer[String, String]

  def newDriver[F[_]: Concurrent: ContextShift: Parallel: Logger](
      config: KafkaConsumerConfig,
      errorHandler: ConsumingErrorHandler[F]
  )(implicit consumerFactory: ConsumerFactory): F[KafkaConsumerDriverApi[F]] =
    Sync[F].delay(new KafkaConsumerDriver[F](config, errorHandler, consumerFactory))

  def newDriverResource[F[_]: Concurrent: ContextShift: Parallel: Logger](
      config: KafkaConsumerConfig,
      errorHandler: ConsumingErrorHandler[F]
  )(implicit consumerFactory: ConsumerFactory): Resource[F, KafkaConsumerDriverApi[F]] =
    Resource.make(newDriver(config, errorHandler))(driver => driver.stop)

}

object KafkaConsumer {
  def topicPartitionFrom(xs: List[PartitionInfo]): List[TopicPartition] =
    xs.map(x => new TopicPartition(x.topic(), x.partition()))
  case class OffsetRange(start: Long, end: Long)
}

trait ConsumingErrorHandler[F[_]] {
  def handleErrors(errors: List[ConsumingError]): F[Unit]
}

object ConsumingErrorHandler {

  def noOp[F[_]: Monad]: ConsumingErrorHandler[F] = new NoOpConsumingErrorHandler[F]

  private class NoOpConsumingErrorHandler[F[_]: Monad] extends ConsumingErrorHandler[F] {
    def handleErrors(errors: List[ConsumingError]): F[Unit] = Monad[F].pure(())
  }

}

sealed trait ConsumingError

case class ConsumingRecordsError(records: List[ConsumerRecord[String, String]], exception: Throwable)
    extends ConsumingError

case class ConsumingRecordError(record: ConsumerRecord[String, String], exception: Throwable) extends ConsumingError

trait KafkaConsumerProcessor[F[_]] {

  def consume(records: List[ConsumerRecord[String, String]]): F[List[Either[ConsumingError, Unit]]]

}

object KafkaConsumerProcessor {

  type Record = ConsumerRecord[String, String]

  def recordSyncConsumer[F[_]: MonadError[*[_], Throwable]](f: Record => F[Unit]): KafkaConsumerProcessor[F] =
    new KafkaConsumerProcessor[F] {
      def consume(records: List[Record]): F[List[Either[ConsumingError, Unit]]] = records.traverse(consumeRecord(f))
    }

  def valueSyncConsumer[F[_]](f: String => F[Unit])(implicit M: MonadError[F, Throwable]): KafkaConsumerProcessor[F] =
    recordSyncConsumer(record => f(record.value))

  def recordParallelConsumer[F[_]: Parallel: MonadError[*[_], Throwable]](
      f: Record => F[Unit]
  ): KafkaConsumerProcessor[F] =
    new KafkaConsumerProcessor[F] {
      def consume(records: List[Record]): F[List[Either[ConsumingError, Unit]]] = records.parTraverse(consumeRecord(f))
    }

  private def consumeRecord[F[_]: MonadError[*[_], Throwable]](
      f: Record => F[Unit]
  )(record: Record): F[Either[ConsumingError, Unit]] =
    f(record).attempt.map {
      case Left(err) => ConsumingRecordError(record, err).asLeft
      case _         => ().asRight
    }

}

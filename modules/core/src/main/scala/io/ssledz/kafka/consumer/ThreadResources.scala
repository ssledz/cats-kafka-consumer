package io.ssledz.kafka.consumer

import cats.effect.{Blocker, Resource, Sync}

import java.util.concurrent.Executors.newSingleThreadExecutor
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{ExecutorService, ThreadFactory}
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

private[consumer] object ThreadResources {

  def kafka[F[_]: Sync](clientId: String): Resource[F, KafkaBlocker] =
    make(newSingleThreadExecutor(newThreadFactory(s"kafka-consumer-polling.$clientId")))
      .map { case (_, ec) => KafkaBlocker(Blocker.liftExecutionContext(ec)) }

  private def make[F[_]: Sync](es: => ExecutorService): Resource[F, (ExecutorService, ExecutionContextExecutor)] =
    Resource.make(Sync[F].delay((es, ExecutionContext.fromExecutor(es)))) { case (es, _) =>
      Sync[F].delay(es.shutdown())
    }

  private def newThreadFactory(name: String): ThreadFactory =
    new ThreadFactory {
      val ctr = new AtomicInteger(0)

      def newThread(r: Runnable): Thread = {
        val back = new Thread(r)
        back.setName(s"$name-${ctr.getAndIncrement()}")
        back.setDaemon(true)
        back
      }
    }

  case class KafkaBlocker(underlying: Blocker)

}

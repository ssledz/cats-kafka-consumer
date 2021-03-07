package io.ssledz.kafka.consumer

import cats.effect.concurrent.MVar
import cats.effect.implicits._
import cats.effect.{Bracket, Concurrent}
import cats.implicits._

private[consumer] final class MLock[F[_]: Bracket[*[_], Throwable]](mVar: MVar[F, Unit]) {

  def acquire: F[Unit] = mVar.take

  def release: F[Unit] = mVar.put(())

  def greenLight[A](fa: F[A]): F[A] = acquire.bracket(_ => fa)(_ => release)
}

object MLock {
  def apply[F[_]: Concurrent: Bracket[*[_], Throwable]]: F[MLock[F]] = MVar[F].of(()).map(ref => new MLock(ref))
}

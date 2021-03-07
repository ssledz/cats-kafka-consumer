package io.ssledz.suite

import cats.effect.{ContextShift, IO, Timer}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import scala.concurrent.ExecutionContext

trait PureTestSuite extends AnyFunSuite with ScalaCheckDrivenPropertyChecks with CatsEquality {

  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  implicit val timer: Timer[IO]     = IO.timer(ExecutionContext.global)

}

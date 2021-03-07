package io.ssledz

import io.estatico.newtype.Coercible
import io.estatico.newtype.ops._
import org.scalacheck.Gen

object generators {

  def cbStrGen[A: Coercible[String, *]]: Gen[A] = nonEmptyStringGen.map(_.coerce[A])

  def cbIntGen[A: Coercible[Int, *]]: Gen[A] = Gen.posNum[Int].map(_.coerce[A])

  def cbLongGen[A: Coercible[Long, *]]: Gen[A] = Gen.posNum[Long].map(_.coerce[A])

  val nonEmptyStringGen: Gen[String] =
    Gen
      .chooseNum(21, 40)
      .flatMap { n =>
        Gen.buildableOfN[String, Char](n, Gen.alphaChar)
      }

}

package io.ssledz

import io.estatico.newtype.Coercible
import io.ssledz.generators.{cbIntGen, cbLongGen, cbStrGen}
import org.scalacheck.Arbitrary

object arbitraries {

  implicit def arbCoercibleInt[A: Coercible[Int, *]]: Arbitrary[A] = Arbitrary(cbIntGen[A])

  implicit def arbCoercibleLong[A: Coercible[Long, *]]: Arbitrary[A] = Arbitrary(cbLongGen[A])

  implicit def arbCoercibleStr[A: Coercible[String, *]]: Arbitrary[A] = Arbitrary(cbStrGen[A])

}

/*
 * Copyright 2016 Daniel Urban
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.sigs.seals
package laws

import cats.Eq
import cats.data.Xor
import cats.kernel.laws._
import cats.instances.all._
import org.typelevel.discipline.Laws
import org.scalacheck.Arbitrary
import org.scalacheck.Prop
import org.scalacheck.Prop._

object AtomicLaws {
  def apply[A](implicit arb: Arbitrary[A], atc: Atomic[A], equ: Eq[A]): AtomicLaws[A] = new AtomicLaws[A] {
    def Arb = arb
    def Atc = atc
    def Equ = equ
  }
}

trait AtomicLaws[A] extends Laws {

  implicit def Arb: Arbitrary[A]
  implicit def Atc: Atomic[A]
  implicit def Equ: Eq[A]

  def roundtrip = new AtomicRuleSet(
    name = "roundtrip",
    "stringRepr-fromString" -> forAll { (a: A) =>
      Atc.fromString(Atc.stringRepr(a)) ?== Xor.right(a)
    }
  )

  final class AtomicRuleSet(
    val name: String,
    val props: (String, Prop)*
  ) extends RuleSet with HasOneParent {
    val parent = None
    val bases = Nil
  }
}
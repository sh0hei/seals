/*
 * Copyright 2016-2017 Daniel Urban and contributors listed in AUTHORS
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
package circe

import io.circe._
import cats.implicits._

object Codecs extends Codecs

trait Codecs {

  // TODO: these also provide codecs for String, Int, ...
  // (every Atom), and when imported, they have a higher
  // priority than the default codecs in circe

  implicit def encoderFromReified[A](implicit A: Reified[A]): Encoder[A] = new Encoder[A] {
    override def apply(a: A): Json = {
      val obj = A.fold(a)(Reified.Folder.instance[Json, JsonObject](
        atom = a => Json.fromString(a.stringRepr),
        hNil = () => JsonObject.empty,
        hCons = (l, h, t) => (l.name, h) +: t,
        prod = Json.fromJsonObject,
        sum = (l, v) => Json.obj(l.name -> v),
        vector = v => Json.arr(v: _*)
      ))
      A.close(obj, Json.fromJsonObject)
    }
  }

  implicit def decoderFromReified[A](implicit A: Reified[A]): Decoder[A] = new Decoder[A] {
    override def apply(c: HCursor): Decoder.Result[A] = {
      val x = A.unfold(Reified.Unfolder.instance[HCursor, DecodingFailure, (Boolean, HCursor)](
        atom = { cur => cur.as[String](Decoder.decodeString).map(s => Reified.StringResult(s, cur)) },
        atomErr = { (cur, err) =>
          DecodingFailure(sh"error while decoding atom: '${err.msg}'", cur.history)
        },
        hNil = { cur => cur.as[JsonObject](Decoder.decodeJsonObject).map(_ => cur) },
        hCons = { (cur, sym) =>
          val c2 = cur.downField(sym.name)
          c2.success
            .toRight(left = DecodingFailure(sh"missing key: '${sym.name}'", c2.history))
            .map { hc =>
              Either.right((hc, (_: HCursor) => Either.right(cur)))
            }
        },
        cNil = { cur =>
          DecodingFailure("no variant matched (CNil reached)", cur.history)
        },
        cCons = { (cur, sym) =>
          cur.downField(sym.name).success.fold {
            Either.right(Either.right[HCursor, HCursor](cur))
          } { hc =>
            Either.right(Either.left(hc))
          }
        },
        vectorInit = { cur =>
          for {
            // make sure it's an array (we depend
            // on this in `vectorFold`):
            _ <- cur.as[Vector[HCursor]].leftMap { cur =>
              DecodingFailure("not an array", cur.history)
            }
          } yield {
            // TODO: this is a workaround - we preserve
            // the cursor in the state, because the one
            // passed on by `unfold` won't always be correct
            // (the `unfold` signature is not general enough,
            // for JSON we'd need something to move up the
            // cursor after decoding, e.g., a CCons).
            (cur, (true, cur))
          }
        },
        vectorFold = { case (_, (first, cur)) =>
          val cur2 = if (first) {
            // always succeeds, since we
            // checked it in `vectorInit`
            cur.downArray
          } else {
            // fails at the end of the array
            cur.right
          }
          Right(cur2.success.map(x => (x, (false, x))))
        },
        unknownError = { msg => DecodingFailure(msg, Nil) }
      ))(c)

      x.map { case (a, _) => a }
    }
  }
}

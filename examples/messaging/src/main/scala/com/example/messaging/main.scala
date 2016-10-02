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

package com.example.messaging

import org.http4s._
import org.http4s.dsl._
import org.http4s.server.{ Server, ServerApp }
import org.http4s.circe._

import scalaz.concurrent.Task

import io.circe._

import io.sigs.seals._
import io.sigs.seals.circe.EnvelopeCodec._

object Protocol {
  final case class Ping(seqNr: Long)
  final case class Pong(orig: Ping)
  final case class PingIncompatible(seqNr: Long, flags: Int)
}

object MyClient extends App {

  import org.http4s.client.blaze._
  import Protocol._

  val client = PooledHttp1Client()

  val pongGood = jsonEncoderOf[Envelope[Ping]].toEntity(
    Envelope(Ping(42L))
  ).flatMap(ping).run
  assert(pongGood == Pong(Ping(42L)))
  println(pongGood)

  try {
    jsonEncoderOf[Envelope[PingIncompatible]].toEntity(
      Envelope(PingIncompatible(99L, 0))
    ).flatMap(ping).run
  } finally {
    client.shutdownNow()
  }

  def ping(ping: EntityEncoder.Entity): Task[Pong] = {
    for {
      pong <- client
        .expect(Request(
          POST,
          Uri(authority = Some(Uri.Authority(port = Some(1234))), path = "/test"),
          body = ping.body
        ))(jsonOf[Envelope[Pong]])
    } yield pong.value
  }
}

object MyServer extends ServerApp {

  import org.http4s.server.blaze._
  import Protocol._

  val service = HttpService {
    case p @ POST -> Root / "test" =>
      for {
        env <- p.as(jsonOf[Envelope[Ping]])
        resp <- Ok(Envelope(Pong(env.value)))(jsonEncoderOf)
      } yield resp
  }

  override def server(args: List[String]) = {
    BlazeBuilder
      .bindHttp(1234, "localhost")
      .mountService(service, "/")
      .start
  }
}
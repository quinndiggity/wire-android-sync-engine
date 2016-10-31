/*
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.model

import com.waz.utils.{JsonDecoder, JsonEncoder}
import org.json.JSONObject

import scala.collection.Searching

class Recipients private (val users: IndexedSeq[UserId]) {
  import Searching._

  lazy val zipWithIndex = users.zipWithIndex

  def size = users.size

  def indexOf(id: UserId) = users.search(id) match {
    case Found(index) => index
    case _ => -1
  }

  def indicesOf(ids: Seq[UserId]) = ids map { users.search(_) } collect { case Found(index) => index }
}

object Recipients {

  def apply(users: Traversable[UserId]) = new Recipients(users.toIndexedSeq.sorted)

  def fromSorted(users: IndexedSeq[UserId]) = new Recipients(users)

  implicit object Encoder extends JsonEncoder[Recipients] {
    override def apply(v: Recipients): JSONObject = JsonEncoder { obj =>
      obj.put("recipients", JsonEncoder.array(v.users) { case (arr, id) => arr.put(id.str) })
    }
  }

  implicit object Decoder extends JsonDecoder[Recipients] {
    import JsonDecoder._
    override def apply(implicit js: JSONObject): Recipients =
      Recipients.fromSorted(decodeUserIdSeq('recipients).toIndexedSeq)
  }
}

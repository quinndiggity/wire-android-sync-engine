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

import android.database.Cursor
import com.waz.db.Col._
import com.waz.db.{Dao, Table}
import com.waz.utils.{JsonDecoder, JsonEncoder}
import org.json.JSONObject
import org.threeten.bp.Instant

import scala.collection.{BitSet, Searching}

/**
  * Keeps info about message recipients.
  *
  * @param id - message Id
  * @param recipients - list of users who should receive the message
  * @param delivered - users who confirmed message delivery (set of indices from `recipients` list)
  * @param read - users who sent read receipt for this message (set of indices from `recipients` list)
  * @param time - message timestamp, we may use that to drop info for old messages
  */
case class ReceiptData(id: MessageId,
                  recipients: Recipients,
                  delivered: BitSet = BitSet.empty,
                  read: BitSet = BitSet.empty,
                  time: Instant = Instant.now()
                 ) {

  lazy val deliveredUsers: Seq[UserId] = recipients.zipWithIndex.collect {
    case (user, index) if delivered(index) => user
  }

  lazy val readUsers: Seq[UserId] = recipients.zipWithIndex.collect {
    case (user, index) if read(index) => user
  }

  def withDelivered(users: UserId*) = copy(
    delivered = delivered ++ recipients.indicesOf(users)
  )

  def withRead(users: UserId*) = copy(
    read = read ++ recipients.indicesOf(users)
  )
}

object ReceiptData {

  implicit object ReceiptDataDao extends Dao[ReceiptData, MessageId] {
    val Id = id[MessageId]('_id, "PRIMARY KEY").apply(_.id)
    val Recipients = json[Recipients]('recipients).apply(_.recipients)
    val Delivered = bitSet('delivered)(_.delivered)
    val Read = bitSet('read)(_.read)
    val Time = timestamp('time)(_.time)

    override val idCol = Id
    override val table = new Table[ReceiptData]("Receipts", Id, Recipients, Delivered, Read, Time)

    override def apply(implicit c: Cursor): ReceiptData = ReceiptData(Id, Recipients, Delivered, Read, Time)
  }
}

class Recipients private (val users: IndexedSeq[UserId]) {
  import Searching._

  lazy val zipWithIndex = users.zipWithIndex

  def indexOf(id: UserId) = users.search(id) match {
    case Found(index) => index
    case _ => -1
  }

  def indicesOf(ids: Seq[UserId]) = ids map { users.search(_) } collect { case Found(index) => index }
}

object Recipients {

  def apply(users: Seq[UserId]) = new Recipients(users.sorted.toIndexedSeq)

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

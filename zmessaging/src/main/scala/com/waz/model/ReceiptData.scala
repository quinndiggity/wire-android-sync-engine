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
import org.threeten.bp.Instant

import scala.collection.BitSet

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

  def isMessageDelivered = delivered.size == recipients.size

  def isMessageRead = read.size == recipients.size
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

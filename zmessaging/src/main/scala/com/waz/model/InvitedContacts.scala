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
import android.database.sqlite.SQLiteDatabase
import com.waz.db.Col._
import com.waz.db.Dao
import org.threeten.bp.Instant

object InvitedContacts {
  def load(implicit db: SQLiteDatabase) = InvitedContactsDao.iterating(InvitedContactsDao.listCursor).acquire(_.map(_._1).toSet)
  def invited(ids: Iterable[ContactId], lastUpdate: Instant)(implicit db: SQLiteDatabase): Unit = InvitedContactsDao.insertOrReplace(ids.iterator.map(id => (id, lastUpdate)))
  def forget(ids: Traversable[ContactId])(implicit db: SQLiteDatabase): Unit = InvitedContactsDao.deleteEvery(ids)

  object InvitedContactsDao extends Dao[(ContactId, Instant), ContactId] {
    val Id = id[ContactId]('_id, "PRIMARY KEY").apply(_._1)
    val LastUpdate = timestamp('last_update)(_._2)

    override val idCol = Id
    override val table = Table("InvitedContacts", Id, LastUpdate)

    override def apply(implicit cursor: Cursor): (ContactId, Instant) = (Id, LastUpdate)
  }
}

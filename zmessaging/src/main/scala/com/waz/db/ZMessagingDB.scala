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
package com.waz.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.waz.ZLog._
import com.waz.db.migrate._
import com.waz.model.AddressBook.ContactHashesDao
import com.waz.model.AssetData.AssetDataDao
import com.waz.model.CallLogEntry.CallLogEntryDao
import com.waz.model.CommonConnectionsData.CommonConnectionsDataDao
import com.waz.model.Contact.{ContactsDao, ContactsOnWireDao, EmailAddressesDao, PhoneNumbersDao}
import com.waz.model.ConversationData.ConversationDataDao
import com.waz.model.ConversationMemberData.ConversationMemberDataDao
import com.waz.model.EditHistory.EditHistoryDao
import com.waz.model.ErrorData.ErrorDataDao
import com.waz.model.InvitedContacts.InvitedContactsDao
import com.waz.model.KeyValueData.KeyValueDataDao
import com.waz.model.Liking.LikingDao
import com.waz.model.MessageData.MessageDataDao
import com.waz.model.MsgDeletion.MsgDeletionDao
import com.waz.model.NotificationData.NotificationDataDao
import com.waz.model.ReceiptData.ReceiptDataDao
import com.waz.model.SearchQueryCache.SearchQueryCacheDao
import com.waz.model.UserData.UserDataDao
import com.waz.model.VoiceParticipantData.VoiceParticipantDataDao
import com.waz.model.otr.UserClients.UserClientsDao
import com.waz.model.sync.SyncJob.SyncJobDao

class ZMessagingDB(context: Context, dbName: String) extends DaoDB(context.getApplicationContext, dbName, null, ZMessagingDB.DbVersion) {

  implicit val logTag = logTagFor[ZMessagingDB]

  val daos = Seq (
    UserDataDao, SearchQueryCacheDao, AssetDataDao, ConversationDataDao,
    ConversationMemberDataDao, MessageDataDao, KeyValueDataDao,
    SyncJobDao, CommonConnectionsDataDao, VoiceParticipantDataDao, NotificationDataDao, ErrorDataDao,
    ContactHashesDao, ContactsOnWireDao, InvitedContactsDao, UserClientsDao, LikingDao,
    ContactsDao, EmailAddressesDao, PhoneNumbersDao, CallLogEntryDao, MsgDeletionDao, EditHistoryDao,
    ReceiptDataDao
  )

  override val migrations = Seq(
    Migration(60, 61)(UserDataMigration.v61),
    Migration(61, 62) { _.execSQL("ALTER TABLE Errors ADD COLUMN messages TEXT") },
    Migration(62, 63) { _.execSQL("ALTER TABLE VoiceParticipants ADD COLUMN sends_video INTEGER DEFAULT 0") },
    Migration(63, 64)(ConversationDataMigration.v64),
    Migration(64, 65) { _.execSQL("ALTER TABLE GcmData ADD COLUMN clear_time INTEGER") },
    Migration(65, 66)(CallLogMigration.v66),
    Migration(66, 67)(LikingsMigration.v67),
    Migration(67, 68) { db =>
      db.execSQL("DROP TABLE IF EXISTS MsgDeletion")
      db.execSQL("CREATE TABLE MsgDeletion (message_id TEXT PRIMARY KEY, timestamp INTEGER)")
    },
    Migration(68, 69)(MessageDataMigration.v69),
    Migration(69, 70)(AssetDataMigration.v70),
    Migration(70, 71) {
      _.execSQL("ALTER TABLE Messages ADD COLUMN edit_time INTEGER DEFAULT 0")
    },
    Migration(70, 71) {
      _.execSQL("ALTER TABLE Messages ADD COLUMN edit_time INTEGER DEFAULT 0")
    },
    Migration(71, 72) { db =>
      MessageDataMigration.v72(db)
      ConversationDataMigration.v72(db)
      ConversationMembersMigration.v72(db)
    },
    Migration(72, 73) {
      _.execSQL("CREATE TABLE EditHistory (original_id TEXT PRIMARY KEY, updated_id TEXT, timestamp INTEGER)")
    },
    Migration(73, 74) { implicit db =>
      db.execSQL("DROP TABLE IF EXISTS GcmData") // just dropping all data, not worth the trouble to migrate that
      db.execSQL("CREATE TABLE NotificationData (_id TEXT PRIMARY KEY, data TEXT)")
    },
    Migration(74, 75) { db =>
      SearchQueryCacheMigration.v75(db)
      SyncJobMigration.v75(db)
    },
    Migration(75, 76) { db =>
      MessageDataMigration.v76(db)
      ConversationDataMigration.v76(db)
    },
    Migration(76, 77) { db =>
      MessageDataMigration.v77(db)
    },
    Migration(77, 78) { db =>
      db.execSQL("CREATE TABLE Receipts (_id TEXT PRIMARY KEY, recipients TEXT, delivered BLOB, read BLOB, time INTEGER)")
    }
  )

  override def onUpgrade(db: SQLiteDatabase, from: Int, to: Int): Unit = {
    if (from < 60) {
      dropAllTables(db)
      onCreate(db)
    } else super.onUpgrade(db, from, to)
  }
}

object ZMessagingDB {
  val DbVersion = 78
}

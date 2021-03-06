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
import com.waz.api.NotificationsHandler.NotificationType
import com.waz.db.Col._
import com.waz.db.Dao
import com.waz.utils.{EnumCodec, JsonDecoder, JsonEncoder}
import org.json.JSONObject
import org.threeten.bp.Instant

case class NotificationData(id: NotId,
                            msg: String,
                            conv: ConvId,
                            user: UserId,
                            msgType: NotificationType,
                            time: Instant = Instant.now,
                            userName: Option[String] = None,
                            ephemeral: Boolean = false,
                            mentions: Seq[UserId] = Seq.empty,
                            referencedMessage: Option[MessageId] = None,
                            hasBeenDisplayed: Boolean = false)

object NotificationData {

  implicit lazy val Decoder: JsonDecoder[NotificationData] = new JsonDecoder[NotificationData] {
    import JsonDecoder._

    override def apply(implicit js: JSONObject): NotificationData = NotificationData(NotId('id: String), 'message, 'conv, 'user,
      NotificationCodec.decode('msgType), 'time, 'userName, 'ephemeral,
      decodeUserIdSeq('mentions), decodeOptId[MessageId]('referencedMessage), 'hasBeenDisplayed)
  }

  implicit lazy val Encoder: JsonEncoder[NotificationData] = new JsonEncoder[NotificationData] {
    override def apply(v: NotificationData): JSONObject = JsonEncoder { o =>
      o.put("id", v.id.str)
      o.put("message", v.msg)
      o.put("conv", v.conv.str)
      o.put("user", v.user.str)
      o.put("msgType", NotificationCodec.encode(v.msgType))
      o.put("time", v.time.toEpochMilli)
      o.put("ephemeral", v.ephemeral)
      o.put("hasBeenDisplayed", v.hasBeenDisplayed)
      v.userName foreach (o.put("userName", _))
      if (v.mentions.nonEmpty) o.put("mentions", JsonEncoder.arrString(v.mentions.map(_.str)))
      v.referencedMessage foreach (o.put("referencedMessage", _))
    }
  }

  implicit object NotificationDataDao extends Dao[NotificationData, NotId] {
    val Id = id[NotId]('_id, "PRIMARY KEY").apply(_.id)
    val Data = text('data)(JsonEncoder.encodeString(_))

    override val idCol = Id
    override val table = Table("NotificationData", Id, Data)

    override def apply(implicit cursor: Cursor): NotificationData = JsonDecoder.decode(cursor.getString(1))
  }

  implicit val NotificationOrdering: Ordering[NotificationData] = Ordering.by((data: NotificationData) => (data.time, data.id))

  implicit lazy val NotificationCodec: EnumCodec[NotificationType, String] = EnumCodec.injective {
    case NotificationType.CONNECT_REQUEST => "ConnectRequest"
    case NotificationType.CONNECT_ACCEPTED => "ConnectAccepted"
    case NotificationType.CONTACT_JOIN => "ContactJoin"
    case NotificationType.ASSET => "Asset"
    case NotificationType.ANY_ASSET => "AnyAsset"
    case NotificationType.VIDEO_ASSET => "VideoAsset"
    case NotificationType.AUDIO_ASSET => "AudioAsset"
    case NotificationType.TEXT => "Text"
    case NotificationType.MEMBER_JOIN => "MemberJoin"
    case NotificationType.MEMBER_LEAVE => "MemberLeave"
    case NotificationType.RENAME => "Rename"
    case NotificationType.KNOCK => "Knock"
    case NotificationType.MISSED_CALL => "MissedCall"
    case NotificationType.LIKE => "Like"
    case NotificationType.LOCATION => "Location"
    case NotificationType.MESSAGE_SENDING_FAILED => "MessageSendingFailed"
  }
}

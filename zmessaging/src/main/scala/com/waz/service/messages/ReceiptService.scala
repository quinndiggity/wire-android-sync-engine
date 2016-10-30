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
package com.waz.service.messages

import com.waz.ZLog._
import com.waz.api.Message.Status.DELIVERED
import com.waz.api.Message.Type._
import com.waz.content.{ConversationStorage, MessagesStorage, ReceiptsStorage}
import com.waz.model.ConversationData.ConversationType.OneToOne
import com.waz.model.{MessageId, UserId}
import com.waz.sync.SyncServiceHandle
import com.waz.threading.Threading
import com.waz.utils._
import com.waz.utils.events.EventContext

import scala.concurrent.Future
import scala.concurrent.Future.successful

class ReceiptService(messages: MessagesStorage, receipts: ReceiptsStorage, convs: ConversationStorage, sync: SyncServiceHandle, selfUserId: UserId) {
  import EventContext.Implicits.global
  import ImplicitTag._
  import Threading.Implicits.Background

  messages.onAdded { msgs =>
    Future.traverse(msgs.iterator.filter(msg => msg.userId != selfUserId && confirmable(msg.msgType))) { msg =>
      convs.get(msg.convId).map(_.filter(_.convType == OneToOne)).flatMapSome { _ =>
        verbose(s"will send receipt for $msg")
        sync.postReceipt(msg.convId, msg.id, msg.userId)
      }
    }.logFailure()
  }

  messages.onDeleted { receipts.remove(_) }

  val confirmable = Set(TEXT, TEXT_EMOJI_ONLY, ASSET, ANY_ASSET, VIDEO_ASSET, AUDIO_ASSET, KNOCK, RICH_MEDIA, HISTORY_LOST, LOCATION)

  def processReceipts(receipts: Seq[MessageId]) =
    if (receipts.nonEmpty) {
      debug(s"received receipts: $receipts")
      messages.updateAll2(receipts, _.copy(state = DELIVERED))
    } else successful(Seq.empty)
}

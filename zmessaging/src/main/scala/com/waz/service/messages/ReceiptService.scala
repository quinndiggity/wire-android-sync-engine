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
import com.waz.api.Message.Status
import com.waz.api.Message.Type._
import com.waz.content.{ConversationStorage, MessagesStorage, ReceiptsStorage}
import com.waz.model.ConversationData.ConversationType.OneToOne
import com.waz.model._
import com.waz.model.sync.ReceiptType
import com.waz.sync.SyncServiceHandle
import com.waz.threading.Threading
import com.waz.utils._
import com.waz.utils.events.EventContext

import scala.concurrent.Future

class ReceiptService(messages: MessagesStorage, receipts: ReceiptsStorage, convs: ConversationStorage, sync: SyncServiceHandle, selfUserId: UserId) {
  import EventContext.Implicits.global
  import ImplicitTag._
  import Threading.Implicits.Background

  val confirmable = Set(TEXT, TEXT_EMOJI_ONLY, ASSET, ANY_ASSET, VIDEO_ASSET, AUDIO_ASSET, KNOCK, RICH_MEDIA, HISTORY_LOST, LOCATION)

  messages.onAdded { msgs =>
    Future.traverse(msgs.iterator.filter(msg => msg.userId != selfUserId && confirmable(msg.msgType))) { msg =>
      convs.get(msg.convId).map(_.filter(_.convType == OneToOne)).flatMapSome { _ =>
        verbose(s"will send receipt for $msg")
        sync.postReceipt(msg.convId, msg.id, msg.userId, ReceiptType.Delivery)
      }
    }.logFailure()
  }

  messages.onDeleted { receipts.remove(_) }

  messages.onMessageSent { msg =>
    // add ReceiptData for messages sent in group conversation
    // TODO: we need to know exact set of users this message was sent to (at this point conv members could already be changes)
  }

  def create(id: MessageId, users: Set[UserId]) = receipts.insert(ReceiptData(id, Recipients(users)))

  def addDeliveryReceipt(conv: ConvId, msg: MessageId, userId: UserId) = {

    def setMessageDelivered() = // set DELIVERED if not READ already
      messages.update(msg, { m =>
        if (m.state == Status.READ || m.convId != conv) m
        else m.copy(state = Status.DELIVERED)
      })

    if (conv.str == userId.str) setMessageDelivered()
    else receipts.update(msg, _.withDelivered(userId)) flatMap {
      case Some((_, data)) if data.isMessageRead => setMessageDelivered()
      case _ => Future successful None
    }
  }

  def addReadReceipt(msg: MessageData, userId: UserId) =
    if (msg.convId.str == userId.str) { // got read receipt in 1-1
      // we don't store receipts for 1-1, we can just update message state here
      messages.update(msg.id, _.copy(state = Status.READ))
    } else
      receipts.update(msg.id, _.withRead(userId)) flatMap {
        case Some((_, data)) if data.isMessageRead =>
          // update message state and remove receipt data (no longer needed)
          messages.update(msg.id, _.copy(state = Status.READ)) andThen { case _ => receipts.remove(msg.id) }
        case _ => Future successful None
      }
}

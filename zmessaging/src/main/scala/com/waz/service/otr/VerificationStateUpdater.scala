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
package com.waz.service.otr

import com.waz.ZLog._
import com.waz.api.Verification
import com.waz.content.{ConversationStorage, DefaultMembersStorage, OtrClientsStorage, UsersStorage}
import com.waz.model.ConversationData.ConversationType
import com.waz.model._
import com.waz.model.otr.UserClients
import com.waz.utils.Serialized

import scala.collection.breakOut
import scala.concurrent.Future

/**
  * Updates `verified` flag on user and conversation when clients state changes.
  * Conversation state changes generate messages in that conversation.
  * Root cause for conv state change is tracked to determine resulting message type.
  * If conv gets unverified because new client was added, then it's state is changed to UNVERIFIED,
  * if device was manually unverified, then conv state goes to UNKNOWN
  */
class VerificationStateUpdater(selfUserId: UserId, usersStorage: UsersStorage, clientsStorage: OtrClientsStorage, convs: ConversationStorage, membersStorage: DefaultMembersStorage) {
  import Verification._
  import VerificationStateUpdater._
  import com.waz.threading.Threading.Implicits.Background
  import com.waz.utils.events.EventContext.Implicits.global

  private val SerializationKey = serializationKey(selfUserId)

  var updateProcessor: VerificationStateUpdate => Future[Unit] = { _ => Future.successful(()) } // FIXME: ideally this would just be an event stream, but we want to wait until everything is processed

  clientsStorage.onAdded { ucs =>
    onClientsChanged(ucs.map { uc => uc.user -> (uc, ClientAdded)} (breakOut))
  }

  clientsStorage.onUpdated { updates =>
    onClientsChanged(updates.map {
      case update @ (_, uc) => uc.user -> (uc, VerificationChange(update))
    } (breakOut))
  }

  membersStorage.onAdded{ members =>
    Serialized.future(SerializationKey) {
      updateConversations(members.map(_.convId).distinct, members.map { member => member.userId -> MemberAdded } (breakOut))
    }
  }

  membersStorage.onDeleted{ members =>
    Serialized.future(SerializationKey) {
      updateConversations(members.map(_._2).distinct, members.map { _._1 -> Other } (breakOut))
    }
  }

  private[service] def onClientsChanged(changes: Map[UserId, (UserClients, VerificationChange)]) = Serialized.future(SerializationKey) {

    def updateUserVerified(user: UserData) = {
      val clients = changes(user.id)._1.clients.values
      user.copy(verified = user.verified.updated(clients.nonEmpty && clients.forall(_.isVerified)))
    }

    // update `UserData.verified` flag
    def updateUsers() = usersStorage.updateAll2(changes.keys.toSeq, updateUserVerified)

    def collectUserChanges(updates: Seq[(UserData, UserData)]): Map[UserId, VerificationChange] =
      updates.map {
        case (_, u) => u.id -> (if (u.isVerified) Other else changes(u.id)._2)
      } (breakOut)

    for {
      updates     <- updateUsers()
      userChanges = collectUserChanges(updates)
      convs       <- Future.traverse(userChanges.keys) { membersStorage.getActiveConvs }
      _           <- updateConversations(convs.flatten.toSeq.distinct, userChanges)
    } yield ()
  }

  private[service] def updateConversations(ids: Seq[ConvId], changes: Map[UserId, VerificationChange]) = {

    def convUsers = Future.traverse(ids) { convId =>
      for {
        userIds <- membersStorage.getActiveUsers(convId)
        users   <- usersStorage.listAll(userIds)
      } yield convId -> users
    }

    def update(conv: ConversationData, us: Seq[UserData]) = {
      // XXX: this condition is a bit complicated to avoid situations where conversation gets verified just because it temporarily contains just self user
      // this could happen if conv was just created and new members are being added (race condition), or in pending conv requests.
      // FIXME: this is not really correct, if all other users leave group conversation then it should actually get VERIFIED
      val isVerified = us.nonEmpty && us.forall(_.verified == VERIFIED) &&
        (conv.convType == ConversationType.Group || conv.convType == ConversationType.OneToOne) &&
        (us.size > 1 || conv.verified != UNKNOWN)

      def deviceAdded = us.exists(u => changes.get(u.id).contains(ClientAdded))

      val state = (isVerified, conv.verified) match {
        case (false, VERIFIED) if deviceAdded => UNVERIFIED
        case (false, VERIFIED)                => UNKNOWN
        case (true, _)                        => VERIFIED
        case (false, current)                 => current
      }

      conv.copy(verified = state)
    }

    for {
      users     <- convUsers
      usersMap  =  users.filter(_._2.nonEmpty).toMap
      updates   <- convs.updateAll2(usersMap.keys.toSeq, { conv => update(conv, usersMap(conv.id)) })
      _         <- updateProcessor(VerificationStateUpdate(updates, usersMap, changes))
    } yield ()
  }
}

object VerificationStateUpdater {
  private implicit val tag: LogTag = logTagFor[VerificationStateUpdater]

  def serializationKey(userId: UserId) = (userId, "verification-state-update")

  // XXX: small hack to synchronize other operations with verification state updating,
  // we sometimes need to make sure that this state is up to date before proceeding
  def awaitUpdated(userId: UserId) = Serialized.future(serializationKey(userId)) { Future.successful(()) }

  case class VerificationStateUpdate(convUpdates: Seq[(ConversationData, ConversationData)], convUsers: Map[ConvId, Seq[UserData]], changes: Map[UserId, VerificationChange])

  sealed trait VerificationChange
  case object ClientUnverified extends VerificationChange
  case object ClientAdded extends VerificationChange
  case object MemberAdded extends VerificationChange
  case object Other extends VerificationChange

  object VerificationChange {

    def apply(update: (UserClients, UserClients)): VerificationChange = {
      val (prev, uc) = update
      val prevKeys = prev.clients.keySet

      def clientAdded = uc.clients.keysIterator.exists(!prevKeys(_))

      def clientUnverified = uc.clients.exists { case (id, c) => !c.isVerified && prev.clients.get(id).exists(_.isVerified) }

      if (clientAdded) ClientAdded
      else if (clientUnverified) ClientUnverified
      else Other
    }
  }
}

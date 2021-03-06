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
package com.waz.utils

import android.content.Context
import android.os.PowerManager
import com.waz.ZLog.{LogTag, verbose}
import com.waz.threading.CancellableFuture
import com.waz.threading.Threading.Implicits.Background

import scala.concurrent.Future
import scala.concurrent.duration._

class WakeLock(context: Context, level: Int = PowerManager.PARTIAL_WAKE_LOCK)(implicit tag: LogTag) {

  protected val appContext = context.getApplicationContext
  protected lazy val powerManager = appContext.getSystemService(Context.POWER_SERVICE).asInstanceOf[PowerManager]
  protected lazy val wakeLock = powerManager.newWakeLock(level, tag)

  protected def acquire()(implicit srcTag: LogTag): Unit = {
    verbose(s"acquiring wakelock, src: $srcTag")(tag)
    wakeLock.acquire()
  }

  protected def release()(implicit srcTag: LogTag): Unit = {
    verbose(s"releasing wakelock, src: $srcTag")(tag)
    wakeLock.release()
  }

  def apply[A](body: => A)(implicit srcTag: LogTag): A = {
    acquire()(srcTag)
    try {
      body
    } finally {
      release()(srcTag)
    }
  }

  def async[A](body: Future[A])(implicit srcTag: LogTag): Future[A] = {
    acquire()(s"async[$srcTag]")
    returning(body) { _.onComplete(_ => release()(s"async[$srcTag]")) }
  }
}

//To keep wakelock for a given period of time after executing it's code
class TimedWakeLock(context: Context, duration: FiniteDuration)(implicit tag: LogTag) extends WakeLock(context) {

  override def apply[A](body: => A)(implicit srcTag: LogTag): A = {
    acquire()(srcTag)
    try {
      body
    } finally {
      releaseAfterDuration()(srcTag)
    }
  }

  override def async[A](body: Future[A])(implicit srcTag: LogTag): Future[A] = {
    acquire()(s"async[$srcTag]")
    returning(body) { _.onComplete(_ => releaseAfterDuration()(srcTag)) }
  }

  private def releaseAfterDuration()(implicit srcTag: LogTag): Unit = CancellableFuture.delay(duration).onComplete(_ => release()(s"delayed[$srcTag]"))
}

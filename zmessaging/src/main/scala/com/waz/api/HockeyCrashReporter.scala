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
package com.waz.api

import java.io.File

import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.wrappers.URI
import com.waz.znet.ContentEncoder.MultipartRequestContent
import com.waz.znet.Response.SuccessHttpStatus
import com.waz.znet.{Request, Response}

import scala.concurrent.duration._

object HockeyCrashReporter {
  import Threading.Implicits.Background

  def uploadCrashReport(hockeyId: String, dump: File, log: File) = {
    val url = s"https://rink.hockeyapp.net/api/2/apps/$hockeyId/crashes/upload"

    ZMessaging.currentGlobal.client(URI.parse(url), Request.PostMethod, MultipartRequestContent(Seq("attachment0" -> dump, "log" -> log)), timeout = 1.minute) map {
      case Response(SuccessHttpStatus(), _, _) => verbose("crash report successfully sent")
      case resp => error(s"Unexpected response from hockey crash report request: $resp")
    } map { _ =>
      dump.delete()
      log.delete()
    }
  }
}

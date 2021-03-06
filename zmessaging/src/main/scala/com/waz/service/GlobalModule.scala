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
package com.waz.service

import android.content.Context
import com.softwaremill.macwire._
import com.waz.PermissionsService
import com.waz.api.ZmsVersion
import com.waz.bitmap.BitmapDecoder
import com.waz.cache.CacheService
import com.waz.client.RegistrationClient
import com.waz.content.{AccountsStorage, Database, GlobalDatabase}
import com.waz.service.assets.{AssetLoader, GlobalRecordAndPlayService}
import com.waz.service.downloads.DownloadRequest.{AssetFromInputStream, UnencodedAudioAsset, VideoAsset}
import com.waz.service.downloads._
import com.waz.service.images.ImageLoader
import com.waz.service.push.GcmGlobalService
import com.waz.sync.client.{AssetClient, VersionBlacklistClient}
import com.waz.ui.MemoryImageCache
import com.waz.znet._

class GlobalModule(val context: Context, val backend: BackendConfig) { global =>
  lazy val storage: Database = new GlobalDatabase(context)
  lazy val prefs: PreferenceService = wire[PreferenceService]
  lazy val metadata: MetaDataService = wire[MetaDataService]
  lazy val cache: CacheService = new CacheService(context, storage)
  lazy val gcmGlobal = wire[GcmGlobalService]
  lazy val bitmapDecoder: BitmapDecoder = wire[BitmapDecoder]
  lazy val imageCache: MemoryImageCache = wire[MemoryImageCache]
  lazy val network = wire[NetworkModeService]
  lazy val phoneNumbers: PhoneNumberService = wire[PhoneNumberService]
  lazy val timeouts = wire[Timeouts]
  lazy val permissions: PermissionsService = wire[PermissionsService]

  lazy val reporting = wire[GlobalReportingService]

  lazy val decoder = Response.CacheResponseBodyDecoder(cache)
  lazy val loginClient = wire[LoginClient]
  lazy val regClient: RegistrationClient = wire[RegistrationClient]
  lazy val downloader: DownloaderService = wire[DownloaderService]
  lazy val streamLoader: Downloader[AssetFromInputStream] = wire[InputStreamAssetLoader]
  lazy val videoLoader: Downloader[VideoAsset] = wire[VideoAssetLoader]
  lazy val pcmAudioLoader: Downloader[UnencodedAudioAsset] = wire[UnencodedAudioAssetLoader]

  lazy val cacheCleanup = wire[CacheCleaningService]

  lazy val accountsStorage = wire[AccountsStorage]
  lazy val mediaManager = wire[MediaManagerService]
  lazy val recordingAndPlayback = wire[GlobalRecordAndPlayService]
  lazy val tempFiles: TempFileService = wire[TempFileService]

  lazy val clientWrapper: ClientWrapper = ClientWrapper
  lazy val client: AsyncClient = new AsyncClient(decoder, AsyncClient.userAgent(metadata.appVersion.toString, ZmsVersion.ZMS_VERSION), clientWrapper)

  lazy val globalClient = new ZNetClient(global, "", "")
  lazy val imageLoader = {
    val client = new AssetClient(new ZNetClient(this, "", ""))
    val loader: AssetLoader = new AssetLoader(context, downloader, new AssetDownloader(client, cache), streamLoader, videoLoader, pcmAudioLoader, cache)
    new ImageLoader(context, cache, imageCache, bitmapDecoder, permissions, loader) { override def tag = "Global" }
  }

  lazy val blacklistClient = new VersionBlacklistClient(globalClient, backend)
  lazy val blacklist       = new VersionBlacklistService(metadata, prefs, blacklistClient)

  lazy val factory = new ZMessagingFactory(this)

  val lifecycle = new ZmsLifecycle()
}

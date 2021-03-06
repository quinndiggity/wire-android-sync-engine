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
package com.waz.testutils

import java.io.File
import java.net.{URI => JURI}

import com.waz.utils.wrappers.{URI, URIBuilder, URIUtil}
import org.apache.http.client.utils.{URIBuilder => JURIBuilder}

class JavaURIBuilder(uriBuilder: JURIBuilder) extends URIBuilder {
  //TODO figure out which methods map best to java.net.URI
  override def appendPath(path: String)                         = new JavaURIBuilder(uriBuilder.setPath(path))
  override def encodedPath(path: String)                        = ???
  override def appendEncodedPath(path: String)                  = ???
  override def appendQueryParameter(key: String, value: String) = new JavaURIBuilder(uriBuilder.setParameter(key, value))
  override def build                                            = new JavaURI(uriBuilder.build())
}

class JavaURI(uri: JURI) extends URI {
  override def buildUpon                      = new JavaURIBuilder(new JURIBuilder(uri))
  override def getPath                        = uri.getPath
  override def getScheme                      = uri.getScheme
  override def getAuthority                   = uri.getAuthority
  override def getHost                        = uri.getHost
  override def getPathSegments                = ???
  override def getLastPathSegment             = ???
  override def getQueryParameter(key: String) = ???
  override def normalizeScheme                = ???
  override def toString                       = uri.toString
}

object JavaURIUtil extends URIUtil {
  override def parse(uri: String) = new JavaURI(new JURI(uri))
  override def fromFile(file: File) = ???
}

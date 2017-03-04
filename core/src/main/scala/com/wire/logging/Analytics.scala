package com.wire.logging

import com.waz.ZLog.LogTag

trait Analytics {
  def saveException(t: Throwable, description: String)(implicit tag: LogTag): Unit

  def shouldReport(t: Throwable): Boolean
}

object Analytics extends Analytics {

  trait NoReporting {
    self: Throwable =>
  }

  var instance: Analytics = NoAnalytics //to be set by application

  override def saveException(t: Throwable, description: String)(implicit tag: LogTag): Unit = instance.saveException(t, description)

  override def shouldReport(t: Throwable): Boolean = instance.shouldReport(t)
}

object NoAnalytics extends Analytics {

  override def saveException(t: Throwable, description: String)(implicit tag: LogTag): Unit = ()

  override def shouldReport(t: Throwable): Boolean = false
}
import java.lang.Runtime._

import android.Keys._
import com.android.tools.lint.checks.ApiDetector
import sbt.Keys._
import sbt._
import sbtassembly.MappingSet
import SharedSettings._

val MajorVersion = "97"
val MinorVersion = "1" // hotfix release

version in ThisBuild := {
  val jobName = sys.env.get("JOB_NAME")
  val buildNumber = sys.env.get("BUILD_NUMBER")
  val master = jobName.exists(_.endsWith("-master"))
  val buildNumberString = buildNumber.fold("-SNAPSHOT")("." + _)
  if (master) MajorVersion + "." + MinorVersion + buildNumberString
  else MajorVersion + buildNumberString
}

crossPaths in ThisBuild := false
organization in ThisBuild := "com.wire"

scalaVersion in ThisBuild := "2.11.8"

javacOptions in ThisBuild ++= Seq("-source", "1.7", "-target", "1.7", "-encoding", "UTF-8")
scalacOptions in ThisBuild ++= Seq("-feature", "-target:jvm-1.7", "-Xfuture", "-deprecation", "-Yinline-warnings", "-Ywarn-unused-import", "-encoding", "UTF-8")

platformTarget in ThisBuild := "android-23"

licenses in ThisBuild += ("GPL-3.0", url("https://opensource.org/licenses/GPL-3.0"))

resolvers in ThisBuild ++= Seq(
  Resolver.mavenLocal,
  Resolver.jcenterRepo,
  Resolver.bintrayRepo("wire-android", "releases"),
  Resolver.bintrayRepo("wire-android", "snapshots"),
  Resolver.bintrayRepo("wire-android", "third-party"),
  "Maven central 1" at "http://repo1.maven.org/maven2",
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
  "Sonatype OSS Releases" at "https://oss.sonatype.org/content/repositories/releases",
  "Localytics" at "http://maven.localytics.com/public"
)


lazy val licenseHeaders = HeaderPlugin.autoImport.headers := Set("scala", "java", "rs") .map { _ -> GPLv3("2016", "Wire Swiss GmbH") } (collection.breakOut)

lazy val root = Project("zmessaging-android", file("."))
  .aggregate(macrosupport, zmessaging)
  .settings(
    aggregate := false,
    aggregate in clean := true,
    aggregate in (Compile, compile) := true,

    publish := {
      (publish in zmessaging).value
    },
    publishLocal := { (publishLocal in zmessaging).value },
    publishM2 := {
      (publishM2 in zmessaging).value
    }
  )

lazy val zmessaging = project
  .enablePlugins(AutomateHeaderPlugin).settings(licenseHeaders)
  .dependsOn(macrosupport)
  .settings(android.Plugin.androidBuildAar: _*)
  .settings(publishSettings: _*)
  .settings(
    name := "zmessaging-android",
    crossPaths := false,
    platformTarget := "android-23",
    lintDetectors := Seq(ApiDetector.UNSUPPORTED),
    lintStrict := true,
    libraryProject := true,
    typedResources := false,
    sourceGenerators in Compile += generateZmsVersion.taskValue,
    ndkAbiFilter := Seq("armeabi-v7a", "x86"),
    ndkBuild := {
      val jni = ndkBuild.value
      val jniSrc = sourceDirectory.value / "main" / "jni"
      val osx = jni.head / "osx"
      osx.mkdirs()

      s"sh ${jniSrc.getAbsolutePath}/build_osx.sh".!

      jni
    },
    libraryDependencies ++= Seq(
      Deps.supportV4 % Provided,
      "com.koushikdutta.async" % "androidasync" % "2.1.8",
      "com.googlecode.libphonenumber" % "libphonenumber" % "7.1.1", // 7.2.x breaks protobuf
      "com.softwaremill.macwire" %% "macros" % "2.2.2" % Provided,
      "com.google.android.gms" % "play-services-base" % "7.8.0" % Provided exclude("com.android.support", "support-v4"),
      "com.google.android.gms" % "play-services-gcm" % "7.8.0" % Provided,
      Deps.avs % Provided,
      Deps.cryptobox,
      Deps.genericMessage,
      Deps.backendApi,
      "com.wire" % "icu4j-shrunk" % "57.1",
      Deps.spotifyPlayer,
      "org.threeten" % "threetenbp" % "1.3" % Provided,
      "com.googlecode.mp4parser" % "isoparser" % "1.1.7",
      Deps.hockeyApp % Provided,
      Deps.localytics,
      "net.java.dev.jna" % "jna" % "4.2.0",
      "org.robolectric" % "android-all" % RobolectricVersion % Provided
    )
  )

lazy val macrosupport = project
  .enablePlugins(AutomateHeaderPlugin).settings(licenseHeaders)
  .settings(publishSettings: _*)
  .settings(
    version := "3.0",
    crossPaths := false,
    exportJars := true,
    name := "zmessaging-android-macrosupport",
    bintrayRepository := "releases",
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % (scalaVersion in ThisBuild).value % Provided,
      "org.robolectric" % "android-all" % RobolectricVersion % Provided
    )
  )

generateZmsVersion in zmessaging := {
  val file = (sourceManaged in Compile in zmessaging).value / "com"/ "waz" / "api" / "ZmsVersion.java"
  val content = """package com.waz.api;
                  |
                  |public class ZmsVersion {
                  |   public static final String ZMS_VERSION = "%s";
                  |   public static final String AVS_VERSION = "%s";
                  |   public static final int ZMS_MAJOR_VERSION = %s;
                  |   public static final boolean DEBUG = %b;
                  |}
                """.stripMargin.format(version.value, avsVersion, MajorVersion, sys.env.get("BUILD_NUMBER").isEmpty || sys.props.getOrElse("debug", "false").toBoolean)
  IO.write(file, content)
  Seq(file)
}

// lazy val fullCoverage = taskKey[Unit]("Runs all tests and generates coverage report of zmessaging")
//
// fullCoverage := {
//   (test in unit in Test).value
//   (test in mocked in Test).value
//   (test in integration in Test).value
//   (coverageReport in zmessaging).value
// }

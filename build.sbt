import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._
import sbtcrossproject.{crossProject, CrossType}

val SCALA_2_13          = "2.13.0-M2"
val SCALA_2_12          = "2.12.4"
val SCALA_2_11          = "2.11.11"
val targetScalaVersions = Seq(SCALA_2_13, SCALA_2_12, SCALA_2_11)

scalaVersion in ThisBuild := SCALA_2_12

organization in ThisBuild := "org.wvlet.airframe"

val buildSettings = Seq[Setting[_]](
  scalaVersion := SCALA_2_12,
  crossScalaVersions := targetScalaVersions,
  crossPaths := true,
  publishMavenStyle := true,
  logBuffered in Test := false,
  scalacOptions ++= Seq("-feature", "-deprecation"),
  sonatypeProfileName := "org.wvlet",
  licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html")),
  homepage := Some(url("https://github.com/wvlet/airframe")),
  scmInfo := Some(
    ScmInfo(
      browseUrl = url("https://github.com/wvlet/airframe"),
      connection = "scm:git@github.com:wvlet/airframe.git"
    )
  ),
  developers := List(
    Developer(id = "leo", name = "Taro L. Saito", email = "leo@xerial.org", url = url("http://xerial.org/leo"))
  ),
  // Use sonatype resolvers
  resolvers ++= Seq(
    Resolver.sonatypeRepo("releases"),
    Resolver.sonatypeRepo("snapshots")
  ),
  // Release settings
  releaseCrossBuild := true,
  releasePublishArtifactsAction := PgpKeys.publishSigned.value,
  // Workaround for sbt-release 1.0.6
  releaseVersionFile := baseDirectory.value / "version.sbt",
  releaseTagName := { (version in ThisBuild).value },
  releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    runClean,
    runTest,
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
    publishArtifacts,
    setNextVersion,
    commitNextVersion,
    releaseStepCommand("sonatypeReleaseAll"),
    pushChanges
  ),
  publishTo := Some(
    if (isSnapshot.value) {
      Opts.resolver.sonatypeSnapshots
    } else {
      Opts.resolver.sonatypeStaging
    }
  )
)

val jsBuildSettings = Seq[Setting[_]](
  // Skip Scala 2.11 + Scala.js build
  crossScalaVersions := Seq(SCALA_2_12),
  // Do not run tests concurrently
  concurrentRestrictions in Global := Seq(
    Tags.limit(Tags.Test, 1)
  )
  // Workaround for ' JSCom has been closed' issue
  //parallelExecution in ThisBuild := false
)

val noPublish = Seq(
  publishArtifact := false,
  publish := {},
  publishLocal := {}
)

lazy val root =
  project
    .in(file("."))
    .settings(name := "airframe-root")
    .settings(buildSettings)
    .settings(noPublish)
    .aggregate(projectJVM, projectJS)

lazy val projectJVM =
  project.settings(noPublish).aggregate(airframeJVM, surfaceJVM, logJVM, airframeSpecJVM, config, jmx, opts, metrics, tablet)

lazy val projectJS =
  project.settings(noPublish).aggregate(airframeJS, surfaceJS, logJS, airframeSpecJS)

lazy val docs =
  project
    .in(file("docs"))
    .settings(
      name := "docs",
      publishArtifact := false,
      publish := {},
      publishLocal := {},
      // Necessary for publishMicrosite
      git.remoteRepo := "git@github.com:wvlet/airframe.git",
      ghpagesNoJekyll := false,
      micrositeName := "Airframe",
      micrositeDescription := "Libraries for Building Full-Fledged Scala Applications",
      micrositeAuthor := "Taro L. Saito",
      micrositeOrganizationHomepage := "https://github.com/wvlet",
      micrositeHighlightTheme := "ocean",
      micrositeGithubOwner := "wvlet",
      micrositeGithubRepo := "airframe",
      micrositeBaseUrl := "airframe",
      micrositeAnalyticsToken := "UA-98364158-1",
      micrositeDocumentationUrl := "docs",
      //micrositePushSiteWith := GitHub4s,
      //micrositeGithubToken := sys.env.get("GITHUB_REPO_TOKEN"),
      micrositePalette ++= Map(
        "brand-primary"   -> "#2582AA",
        "brand-secondary" -> "#143F56",
        "brand-tertiary"  -> "#042F46",
        "gray-dark"       -> "#453E46",
        "gray"            -> "#534F54"
      ),
      watchSources += new sbt.internal.io.Source(
        sourceDirectory.value,
        new FileFilter {
          def accept(f: File) = !f.isDirectory
        },
        NothingFilter
      )
    )
    .enablePlugins(MicrositesPlugin)

lazy val airframe =
  crossProject(JVMPlatform, JSPlatform)
    .in(file("airframe"))
    .settings(buildSettings)
    .settings(
      name := "airframe",
      description := "Dependency injection library tailored to Scala",
      libraryDependencies ++= Seq(
        "org.scala-lang" % "scala-reflect" % scalaVersion.value,
        // scalatest
        "org.scalatest" %%% "scalatest" % "3.0.4" % "test"
      )
    )
    .jvmSettings(
      // include the macro classes and resources in the main jar
      mappings in (Compile, packageBin) ++= mappings.in(airframeMacrosJVM, Compile, packageBin).value,
      // include the macro sources in the main source jar
      mappings in (Compile, packageSrc) ++= mappings.in(airframeMacrosJVM, Compile, packageSrc).value
    )
    .jsSettings(
      jsBuildSettings,
      // Copy macro classes into the main jar
      mappings in (Compile, packageBin) ++= mappings.in(airframeMacrosJS, Compile, packageBin).value.filter(x => x._2 != "JS_DEPENDENCIES"),
      // include the macro sources in the main source jar
      mappings in (Compile, packageSrc) ++= mappings.in(airframeMacrosJS, Compile, packageSrc).value
    )
    .dependsOn(surface, airframeMacros % "compile-internal,test-internal", airframeSpec % "test")

lazy val airframeJVM = airframe.jvm
lazy val airframeJS  = airframe.js

// Airframe depends on Airframe Macros, so we needed to split the project
lazy val airframeMacros =
  crossProject(JVMPlatform, JSPlatform)
    .in(file("airframe-macros"))
    .settings(buildSettings)
    .settings(
      buildSettings,
      name := "airframe-macros",
      description := "Macros for Airframe",
      libraryDependencies ++= Seq(
        "org.scala-lang" % "scala-reflect" % scalaVersion.value
      ),
      publish := {},
      publishLocal := {}
    )
    .jsSettings(jsBuildSettings)

lazy val airframeMacrosJVM = airframeMacros.jvm
lazy val airframeMacrosJS  = airframeMacros.js

lazy val surface =
  crossProject(JVMPlatform, JSPlatform)
    .in(file("surface"))
    .settings(buildSettings)
    .settings(
      name := "airframe-surface",
      description := "A library for extracting object structure surface",
      libraryDependencies ++= Seq(
        "org.scala-lang" % "scala-reflect"  % scalaVersion.value,
        "org.scala-lang" % "scala-compiler" % scalaVersion.value % "provided",
        // scalatest
        "org.scalatest" %%% "scalatest" % "3.0.4" % "test"
      )
    )
    .jsSettings(jsBuildSettings)
    .dependsOn(log, airframeSpec % "test")

lazy val surfaceJVM = surface.jvm
lazy val surfaceJS  = surface.js

lazy val config =
  project
    .in(file("config"))
    .settings(buildSettings)
    .settings(
      name := "airframe-config",
      description := "airframe configuration module",
      libraryDependencies ++= Seq(
        "org.yaml" % "snakeyaml" % "1.18"
      )
    )
    .dependsOn(surfaceJVM, tablet, airframeSpecJVM % "test")

lazy val jmx =
  project
    .in(file("jmx"))
    .settings(buildSettings)
    .settings(
      name := "airframe-jmx",
      description := "A library for exposing Scala object data through JMX"
    )
    .dependsOn(surfaceJVM, airframeSpecJVM % "test")

lazy val opts =
  project
    .in(file("opts"))
    .settings(buildSettings)
    .settings(
      name := "airframe-opts",
      description := "Command-line option parser",
      libraryDependencies ++= Seq(
        "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.6"
      )
    )
    .dependsOn(surfaceJVM, airframeSpecJVM % "test")

lazy val log =
  crossProject(JVMPlatform, JSPlatform)
    .in(file("log"))
    .settings(buildSettings)
    .settings(
      name := "airframe-log",
      description := "Fancy logger for Scala",
      libraryDependencies ++= Seq(
        "org.scala-lang" % "scala-reflect" % scalaVersion.value % "provided",
        "org.scalatest"  %%% "scalatest"   % "3.0.4"            % "test"
      ) ++ (if (scalaVersion.value.startsWith("2.13."))
              Seq("org.scala-lang.modules" %% "scala-parallel-collections" % "0.1.2")
            else Seq.empty)
    )
    .jvmSettings(
      libraryDependencies ++= Seq("ch.qos.logback" % "logback-core" % "1.2.3")
    )
    .jsSettings(
      jsBuildSettings,
      libraryDependencies ++= Seq(
        "org.scala-js" %%% "scalajs-java-logging" % "0.1.3"
      )
    )

lazy val logJVM = log.jvm
lazy val logJS  = log.js

lazy val metrics =
  project
    .in(file("metrics"))
    .settings(buildSettings)
    .settings(
      name := "airframe-metrics",
      description := "Metrics library for representing duration, size, etc.",
      libraryDependencies ++= Seq()
    )
    .dependsOn(logJVM, airframeSpecJVM % "test")

lazy val airframeSpec =
  crossProject(JVMPlatform, JSPlatform)
    .in(file("spec"))
    .settings(buildSettings)
    .settings(
      name := "airframe-spec",
      description := "Airframe spec test base library",
      libraryDependencies ++= Seq("org.scalatest" %%% "scalatest" % "3.0.4") ++ (if (scalaVersion.value.startsWith("2.13."))
                                                                                   Seq("org.scala-lang.modules" %% "scala-parallel-collections" % "0.1.2")
                                                                                 else Seq.empty)
    )
    .jsSettings(jsBuildSettings)
    .dependsOn(log)

lazy val airframeSpecJVM = airframeSpec.jvm
lazy val airframeSpecJS  = airframeSpec.js

lazy val tablet =
  project
    .in(file("tablet"))
    .settings(buildSettings)
    .settings(
      name := "airframe-tablet",
      description := "Data format conversion library",
      libraryDependencies ++= Seq(
        "org.msgpack" % "msgpack-core" % "0.8.13",
        // scala-csv doesn't support Scala 2.13 yet
        // "com.github.tototoshi" %% "scala-csv"   % "1.3.5",
        // For ColumnType parser and JSON parser
        "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.6",
        "org.scalacheck"         %% "scalacheck"               % "1.13.5" % "test",
        // For JDBC testing
        "org.xerial" % "sqlite-jdbc" % "3.20.1" % "test"
      )
    )
    .dependsOn(logJVM, surfaceJVM, airframeSpecJVM % "test")
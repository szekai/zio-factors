import sbt.{ given, * }
import java.net.URI

val scala3 = "3.8.4"
val zioV   = "2.1.26"

scalaVersion := scala3
organization := "io.github.szekai"

name := "zio-factors"

homepage := Some(URI("https://github.com/szekai/zio-factors"))
licenses := Seq("Apache-2.0" -> URI("https://www.apache.org/licenses/LICENSE-2.0"))
developers := List(
  Developer("szekai", "Sze Kai", "szekai@users.noreply.github.com", url("https://github.com/szekai"))
)
scmInfo := Some(ScmInfo(
  url("https://github.com/szekai/zio-factors"),
  "scm:git:https://github.com/szekai/zio-factors.git",
  "scm:git:git@github.com:szekai/zio-factors.git"
))

// publishTo — managed by sbt-ci-release
// Signing uses the ephemeral CI key (PGP_SECRET) — Sonatype Central does not
// verify signatures, so the Sonatype token is the only real credential.

lazy val root = (project in file("."))
  .settings(
    Test / fork := true,
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-json"     % "0.9.2" % Test,
      "dev.zio" %% "zio-test"     % zioV    % Test,
      "dev.zio" %% "zio-test-sbt" % zioV    % Test
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )

lazy val examples = (project in file("examples"))
  .dependsOn(root)
  .settings(
    name := "zio-factors-examples",
    publish / skip := true,
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-json" % "0.9.2"
    )
  )

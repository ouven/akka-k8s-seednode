import com.typesafe.sbt.packager.docker.{Cmd, ExecCmd}

val `common-settings` = Seq(
  scalacOptions ++= Seq("-deprecation", "-feature"),

  homepage := Some(url("https://github.com/ouven/akka-k8s-seednode/wiki")),
  licenses := Seq(
    "Apache License Version 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"),
    "The New BSD License" -> url("http://www.opensource.org/licenses/bsd-license.html")
  ),

  sources in EditSource <++= baseDirectory.map(d => (d / ".doctmpl" / "README.md").get),
  targetDirectory in EditSource <<= baseDirectory,
  variables in EditSource <+= version { v => ("version", v) },

  // relase with sbt-pgp plugin
  releasePublishArtifactsAction := PgpKeys.publishSigned.value,
  releaseProcess := ReleaseProcess.steps,

  publishTo <<= version { v: String =>
    val nexus = "https://oss.sonatype.org/"
    if (v.trim.endsWith("SNAPSHOT"))
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases" at nexus + "service/local/staging/deploy/maven2")
  },
  publishMavenStyle := true,
  publishArtifact in Test := false,

  pomIncludeRepository := { _ => false },
  pomExtra := <issueManagement>
    <system>github</system>
    <url>https://github.com/ouven/akka-k8s-seednode/issues</url>
  </issueManagement>
    <developers>
      <developer>
        <name>Ruben Wagner</name>
        <url>https://github.com/ouven</url>
        <roles>
          <role>owner</role>
          <role>developer</role>
        </roles>
        <timezone>+1</timezone>
      </developer>
    </developers>
    <scm>
      <url>git@github.com:ouven/akka-k8s-seednode.git</url>
      <connection>scm:git:git@github.com:ouven/akka-k8s-seednode.git</connection>
      <developerConnection>scm:git:git@github.com:ouven/akka-k8s-seednode.git</developerConnection>
    </scm>,

  organization := "de.aktey.akka.k8s",
  version := "1.0.0",
  scalaVersion := "2.12.0",
  crossScalaVersions := Seq("2.11.8", "2.12.0")
)

lazy val `akka-k8s-seednode` = project.in(file("."))
  .aggregate(`seednode-config`, `seednode-config-example`)
  .settings(`common-settings`: _*)
  .settings(
    publishArtifact := false
  )

lazy val `seednode-config` = project
  .settings(`common-settings`: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe" % "config" % "1.3.1"
    )
  )

val akkaVersion = "2.4.12"
val logbackVersion = "1.1.3"

lazy val `seednode-config-example` = project
  .enablePlugins(JavaAppPackaging)
  .dependsOn(`seednode-config`)
  .settings(`common-settings`: _*)
  .settings(
    publishArtifact := false,
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % akkaVersion,
      "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
      "ch.qos.logback" % "logback-classic" % logbackVersion
    ),
    // docker settings
    dockerBaseImage := "java:jre-alpine",
    dockerExposedPorts += 2551,
    dockerCommands := {
      val insertPoint = 2
      dockerCommands.value.take(insertPoint) ++ Seq(
        Cmd("USER", "root"),
        ExecCmd("RUN", "apk", "--update", "add", "bash")
      ) ++ dockerCommands.value.drop(insertPoint)
    },
    version in Docker := "latest"
  )

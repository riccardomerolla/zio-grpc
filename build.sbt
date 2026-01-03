ThisBuild / scalaVersion := "3.3.1"
ThisBuild / organization := "io.github.riccardomerolla"
ThisBuild / organizationName := "Riccardo Merolla"
ThisBuild / organizationHomepage := Some(url("https://github.com/riccardomerolla"))

inThisBuild(List(
  organization := "io.github.riccardomerolla",
  homepage := Some(url("https://github.com/riccardomerolla/zio-grpc")),
  licenses := Seq(
    "MIT" -> url("https://opensource.org/license/mit")
  ),
  developers := List(
    Developer(
      id = "riccardomerolla",
      name = "Riccardo Merolla",
      email = "riccardo.merolla@gmail.com",
      url = url("https://github.com/riccardomerolla")
    )
  ),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/riccardomerolla/zio-grpc"),
      "scm:git@github.com:riccardomerolla/zio-grpc.git"
    )
  ),
  versionScheme := Some("early-semver")
))

val zioVersion = "2.1.22"
val grpcVersion = "1.65.1"

lazy val root = (project in file("."))
  .aggregate(core, server, client, codegen, examples)
  .settings(
    name := "zio-grpc",
    description := "Type-safe gRPC framework for Scala 3 + ZIO",
    publish / skip := true
  )

lazy val core = (project in file("zio-grpc-core"))
  .settings(
    name := "zio-grpc-core",
    description := "Protocol codecs and core data types for ZIO-gRPC.",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % zioVersion,
      "dev.zio" %% "zio-streams" % zioVersion,
      "io.grpc" % "grpc-api" % grpcVersion
    )
  )

lazy val server = (project in file("zio-grpc-server"))
  .dependsOn(core)
  .settings(
    name := "zio-grpc-server",
    description := "Server DSL and handler composition for ZIO-gRPC.",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % zioVersion
    )
  )

lazy val client = (project in file("zio-grpc-client"))
  .dependsOn(core)
  .settings(
    name := "zio-grpc-client",
    description := "Client stubs and channel management for ZIO-gRPC.",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % zioVersion
    )
  )

lazy val codegen = (project in file("zio-grpc-codegen"))
  .dependsOn(core)
  .settings(
    name := "zio-grpc-codegen",
    description := "Proto code generation utilities for ZIO-gRPC.",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % zioVersion
    )
  )

lazy val examples = (project in file("zio-grpc-examples"))
  .dependsOn(core, server, client)
  .settings(
    name := "zio-grpc-examples",
    description := "Example services built with ZIO-gRPC.",
    publish / skip := true,
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % zioVersion
    )
  )

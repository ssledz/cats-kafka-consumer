import sbt._

object Dependencies {

  object Versions {
    val cats             = "2.3.1"
    val catsEffect       = "2.3.1"
    val circe            = "0.13.0"
    val circeConfig      = "0.8.0"
    val betterMonadicFor = "0.3.1"
    val kindProjector    = "0.11.3"
    val catsRetry        = "2.1.0"
    val log4cats         = "1.1.1"
    val logback          = "1.2.3"
    val newtype          = "0.4.3"
    val scalaCheck       = "1.15.2"
    val scalaTest        = "3.2.3"
    val scalaTestPlus    = "3.2.2.0"
    val kafkaClients     = "2.6.0"
    val jacksonDataBind  = "2.11.3"
  }

  object Libraries {
    def circe(artifact: String): ModuleID = "io.circe" %% artifact % Versions.circe

    val cats       = "org.typelevel"    %% "cats-core"   % Versions.cats
    val catsEffect = "org.typelevel"    %% "cats-effect" % Versions.catsEffect
    val catsRetry  = "com.github.cb372" %% "cats-retry"  % Versions.catsRetry

    val kafka           = "org.apache.kafka"           % "kafka-clients"    % Versions.kafkaClients
    val jacksonDataBind = "com.fasterxml.jackson.core" % "jackson-databind" % Versions.jacksonDataBind

    val circeCore    = circe("circe-core")
    val circeGeneric = circe("circe-generic")
    val circeParser  = circe("circe-parser")
    val circeRefined = circe("circe-refined")

    val circeConfig = "io.circe" %% "circe-config" % Versions.circeConfig

    val newtype = "io.estatico" %% "newtype" % Versions.newtype

    // Compiler plugins
    val betterMonadicFor = "com.olegpy"   %% "better-monadic-for" % Versions.betterMonadicFor
    val kindProjector    = "org.typelevel" % "kind-projector"     % Versions.kindProjector

    // Logging
    val log4cats = "io.chrisdavenport" %% "log4cats-slf4j"  % Versions.log4cats
    val logback  = "ch.qos.logback"     % "logback-classic" % Versions.logback

    // Test
    val scalaCheck    = "org.scalacheck"    %% "scalacheck"      % Versions.scalaCheck
    val scalaTest     = "org.scalatest"     %% "scalatest"       % Versions.scalaTest
    val scalaTestPlus = "org.scalatestplus" %% "scalacheck-1-14" % Versions.scalaTestPlus

  }

}

import Dependencies._

ThisBuild / scalaVersion := "2.13.4"
ThisBuild / organization := "io.ssledz"
ThisBuild / dynverSeparator := "-"

addCommandAlias("fmt", ";scalafmt ;test:scalafmt ;scalafmtSbt")
addCommandAlias("showDep", ";dependencyBrowseGraphHtml")

resolvers += Resolver.sonatypeRepo("snapshots")

lazy val root = (project in file("."))
  .settings(
    name := "cats-kafka-consumer",
    description := "Functional wrapper around kafka consumer api"
  )
  .aggregate(core, tests)

lazy val tests = (project in file("modules/tests"))
  .configs(IntegrationTest)
  .enablePlugins(ScalafmtPlugin)
  .settings(
    name := "cats-kafka-consumer-test-suite",
    scalacOptions ++= scalaOptions,
    scalafmtOnCompile := true,
    Defaults.itSettings,
    consoleSettings,
    libraryDependencies ++= Seq(
      typeSystemEnhancements,
      betterMonadicFor,
      Libraries.scalaCheck,
      Libraries.scalaTest,
      Libraries.scalaTestPlus,
      Libraries.logback % Runtime
    )
  )
  .dependsOn(core)

lazy val examples = (project in file("modules/examples"))
  .enablePlugins(ScalafmtPlugin)
  .settings(
    name := "cats-kafka-consumer-examples",
    scalacOptions ++= scalaOptions,
    scalafmtOnCompile := true,
    publishArtifact := false,
    skip in publish := true,
    consoleSettings,
    libraryDependencies ++= Seq(
      typeSystemEnhancements,
      betterMonadicFor,
      Libraries.logback % Runtime
    )
  )
  .dependsOn(core)

lazy val core = (project in file("modules/core"))
  .enablePlugins(ScalafmtPlugin)
  .settings(
    name := "cats-kafka-consumer-core",
    scalacOptions ++= scalaOptions,
    scalafmtOnCompile := true,
    resolvers += Resolver.sonatypeRepo("snapshots"),
    Defaults.itSettings,
    consoleSettings,
    libraryDependencies ++= Seq(
      typeSystemEnhancements,
      betterMonadicFor,
      Libraries.cats,
      Libraries.catsEffect,
      Libraries.catsRetry,
      Libraries.kafka,
      Libraries.jacksonDataBind,
      Libraries.circeCore,
      Libraries.circeGeneric,
      Libraries.circeParser,
      Libraries.circeConfig,
      Libraries.circeRefined,
      Libraries.log4cats,
      Libraries.logback % Runtime,
      Libraries.newtype
    )
  )

// Filter out compiler flags to make the repl experience functional...
lazy val badConsoleFlags = Seq("-Xfatal-warnings", "-Ywarn-unused:imports")

lazy val consoleSettings = Seq(
  initialCommands := "import io.ssledz._",
  scalacOptions in (Compile, console) ~= (_.filterNot(badConsoleFlags.contains(_)))
)

// https://github.com/typelevel/kind-projector
lazy val typeSystemEnhancements = compilerPlugin(Libraries.kindProjector cross CrossVersion.full)

// https://github.com/oleg-py/better-monadic-for
lazy val betterMonadicFor = compilerPlugin(Libraries.betterMonadicFor)

// Instead of defining all these scalac options one can use plugin sbt-tpolecat https://github.com/DavidGregory084/sbt-tpolecat
// https://docs.scala-lang.org/overviews/compiler-options/index.html
lazy val scalaOptions = Seq(
  "-deprecation", // Emit warning and location for usages of deprecated APIs.
  "-encoding",
  "utf-8",                         // Specify character encoding used by source files.
  "-explaintypes",                 // Explain type errors in more detail.
  "-feature",                      // Emit warning and location for usages of features that should be imported explicitly.
  "-language:existentials",        // Existential types (besides wildcard types) can be written and inferred
  "-language:experimental.macros", // Allow macro definition (besides implementation and application)
  "-language:higherKinds",         // Allow higher-kinded types
  "-language:implicitConversions", // Allow definition of implicit functions called views
  "-Ymacro-annotations",           // Enable support for macro annotations, formerly in macro paradise.
  "-unchecked",                    // Enable additional warnings where generated code depends on assumptions.
  "-Xcheckinit",                   // Wrap field accessors to throw an exception on uninitialized access.
//  "-Xfatal-warnings", // Fail the compilation if there are any warnings.
  "-Ywarn-dead-code",              // Warn when dead code is identified.
  "-Ywarn-extra-implicit",         // Warn when more than one implicit parameter section is defined.
  "-Ywarn-numeric-widen",          // Warn when numerics are widened.
  "-Ywarn-unused:implicits",       // Warn if an implicit parameter is unused.
  "-Ywarn-unused:imports",         // Warn if an import selector is not referenced.
  "-Ywarn-unused:locals",          // Warn if a local definition is unused.
  "-Ywarn-unused:params",          // Warn if a value parameter is unused.
  "-Ywarn-unused:patvars",         // Warn if a variable bound in a pattern is unused.
  "-Ywarn-unused:privates",        // Warn if a private member is unused.
  "-Ywarn-value-discard",          // Warn when non-Unit expression results are unused.
  "-Xlint:adapted-args",           // Warn if an argument list is modified to match the receiver.
  "-Xlint:constant",               // Evaluation of a constant arithmetic expression results in an error.
  "-Xlint:delayedinit-select",     // Selecting member of DelayedInit.
  "-Xlint:doc-detached",           // A Scaladoc comment appears to be detached from its element.
  "-Xlint:inaccessible",           // Warn about inaccessible types in method signatures.
  "-Xlint:infer-any",              // Warn when a type argument is inferred to be `Any`.
  "-Xlint:missing-interpolator",   // A string literal appears to be missing an interpolator id.
  "-Xlint:nullary-unit",           // Warn when nullary methods return Unit.
  "-Xlint:option-implicit",        // Option.apply used implicit view.
  "-Xlint:package-object-classes", // Class or object defined in package object.
  "-Xlint:poly-implicit-overload", // Parameterized overloaded implicit methods are not visible as view bounds.
  "-Xlint:private-shadow",         // A private field (or class parameter) shadows a superclass field.
  "-Xlint:stars-align",            // Pattern sequence wildcard must align with sequence component.
  "-Xlint:type-parameter-shadow"   // A local type parameter shadows a type already in scope.
)

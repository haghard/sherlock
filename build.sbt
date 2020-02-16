import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import scalariform.formatter.preferences._

name := "sherlock"
version := "0.0.1"

scalaVersion := "2.12.10"

libraryDependencies ++= Dependencies.Compile.all
libraryDependencies ++= Dependencies.Test.all

resolvers ++= Seq(
  Resolver.defaultLocal,
  Resolver.mavenLocal,
  Resolver.mavenCentral,
  Resolver.sonatypeRepo("public"),
  Resolver.sonatypeRepo("snapshots"),
  Resolver.sonatypeRepo("releases"),
  Resolver.typesafeRepo("releases"),
  "Hyperreal Repository" at "https://dl.bintray.com/edadma/maven"
)

updateOptions := updateOptions.value.withCachedResolution(true)

ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(AlignParameters, true)
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(RewriteArrowSymbols, true)

//comment out for test:run
//fork in run := true

sourceGenerators in Test += Def.task {
  val file = (sourceManaged in Test).value / "amm.scala"
  IO.write(file, """object amm extends App { ammonite.Main().run() }""")
  Seq(file)
}.taskValue

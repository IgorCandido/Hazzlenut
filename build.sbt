name := "Hazzlenut"

version := "0.1"

scalaVersion := "2.12.8"

addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.0")

val scalazZIOInteropCats = "dev.zio" %% "zio-interop-cats" % "2.0.0.0-RC1"
val scalazZIO = "dev.zio" %% "zio" % "1.0.0-RC10-1"
val scalaz = "org.scalaz" %% "scalaz-core" % "7.2.27"
val scalaTest = "org.scalatest" %% "scalatest" % "3.2.0-SNAP10" % Test
val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.14.0" % Test
val akkaHttp = "com.typesafe.akka" %% "akka-http" % "10.1.8"
val akka = "com.typesafe.akka" %% "akka-actor" % "2.5.22"
val akkaStream = "com.typesafe.akka" %% "akka-stream" % "2.5.22"
val akkaHttpClient = "com.typesafe.akka" %% "akka-http-core" % "10.1.8"
val `akka-http-spray-json` = "com.typesafe.akka" %% "akka-http-spray-json" % "10.1.8"
val akkaTestKit = "com.typesafe.akka" %% "akka-testkit" % "2.5.23" % Test
val catseffect = "org.typelevel" %% "cats-effect" % "1.3.1"
val cats = "org.typelevel" %% "cats-core" % "1.6.1"
val dakatsukaOauth = "com.github.dakatsuka" %% "akka-http-oauth2-client" % "0.1.0"
val `log-effect` = "io.laserdisc" %% "log-effect-core" % "0.2.2"
val `log-effect-zio` = "io.laserdisc" %% "log-effect-zio" % "0.8.0"

val circeVersion = "0.10.0"
val circeParse = "io.circe" %% "circe-parser" % circeVersion
val circeCore = "io.circe" %% "circe-core" % circeVersion
val circeGeneric = "io.circe" %% "circe-generic" % circeVersion
val akkaStreamTestKit = "com.typesafe.akka" %% "akka-stream-testkit" % "2.5.19"
val akkaHttpTestKit = "com.typesafe.akka" %% "akka-http-testkit" % "10.1.8"

libraryDependencies ++= Seq(
  cats,
  catseffect,
  scalazZIO,
  scalazZIOInteropCats,
  scalaTest,
  scalaCheck,
  akkaHttp,
  akkaStream,
  akka,
  akkaHttpClient,
  akkaTestKit,
  dakatsukaOauth,
  circeCore,
  circeParse,
  circeGeneric,
  akkaStreamTestKit,
  akkaHttpTestKit,
  `akka-http-spray-json`,
  `log-effect`,
  `log-effect-zio`
)

mainClass in assembly := Some("hazzlenut.Main")
assemblyJarName in assembly := "assembly.jar"

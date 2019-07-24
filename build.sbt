name := "Hazzlenut"

version := "0.1"

scalaVersion := "2.12.8"

addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.0")

val scalazZIOInteropCats = "org.scalaz" %% "scalaz-zio-interop-cats" % "1.0-RC4"
val scalazZIO = "org.scalaz" %% "scalaz-zio" % "1.0-RC4"
val scalaz = "org.scalaz" %% "scalaz-core" % "7.2.27"
val scalaTest = "org.scalatest" %% "scalatest" % "3.2.0-SNAP10" % Test
val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.14.0" % Test
val akkaHttp = "com.typesafe.akka" %% "akka-http" % "10.1.8"
val akka = "com.typesafe.akka" %% "akka-actor" % "2.5.22"
val akkaStream = "com.typesafe.akka" %% "akka-stream" % "2.5.22"
val akkaHttpClient = "com.typesafe.akka" %% "akka-http-core" % "10.1.8"
val `akka-http-spray-json` = "com.typesafe.akka" %% "akka-http-spray-json" % "10.1.8"
val akkaTestKit = "com.typesafe.akka" %% "akka-testkit" % "2.5.23" % Test
val catseffect = "org.typelevel" %% "cats-effect" % "1.2.0"
val cats = "org.typelevel" %% "cats-core" % "0.9.0"
val dakatsukaOauth = "com.github.dakatsuka" %% "akka-http-oauth2-client" % "0.1.0" //TODO check if can upgrade to cats 1.6.1 without this breaking

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
  `akka-http-spray-json`
)

mainClass in assembly := Some("hazzlenut.Main")
assemblyJarName in assembly := "assembly.jar"

organization in ThisBuild := "phobosive"
version in ThisBuild := "0.1-SNAPSHOT"

// the Scala version that will be used for cross-compiled libraries
scalaVersion in ThisBuild := "2.13.1"

val enumeratum = "com.beachape"  %% "enumeratum-play-json"      % "1.5.17"
val enumeratum_slick = "com.beachape" %% "enumeratum-slick" % "1.5.16"
val macwire = "com.softwaremill.macwire" %% "macros" % "2.3.3" % "provided"
val postgresDriver = "org.postgresql" % "postgresql" % "42.2.10"
val kebsSlick = "pl.iterators"  %% "kebs-slick"      % "1.7.1"
val slickPg = "com.github.tminglei" %% "slick-pg"  % "0.18.1"
val scalaMock = "org.scalamock" %% "scalamock" % "4.4.0" % Test
val scalaTest = "org.scalatest" %% "scalatest" % "3.1.0" % Test

lazy val `reservation` = (project in file("."))
  .aggregate(`reservation-api`, `reservation-impl`)

lazy val `reservation-api` = (project in file("reservation-api"))
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslApi,
      enumeratum
    )
  )

lazy val `reservation-impl` = (project in file("reservation-impl"))
  .enablePlugins(LagomScala)
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslPersistenceJdbc,
      lagomScaladslKafkaBroker,
      lagomScaladslTestKit,
      macwire,
      postgresDriver,
      enumeratum,
      kebsSlick,
      slickPg,
      scalaMock,
      scalaTest
    )
  )
  .settings(lagomForkedTestSettings)
  .dependsOn(`reservation-api`)

lagomCassandraEnabled in ThisBuild := false

ThisBuild / scalaVersion     := "2.13.10"
ThisBuild / version          := "0.1.0"
ThisBuild / organization     := "unit"

lazy val root = (project in file("."))
  .settings(
    name := "ziodiff",
    Compile / packageBin / mainClass := Some("Main"),
    (assembly / assemblyJarName) := "ziodiff.jar",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % "2.0.9",
      "dev.zio" %% "zio-test" % "2.0.9" % Test
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )

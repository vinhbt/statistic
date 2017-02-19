import scala.language.postfixOps

lazy val coreSettings = Seq(
  //version := use git describe. see https://github.com/sbt/sbt-git
  scalaVersion := V.scala,
  name := "statistic",
  organization := "com.sandinh",
  version := "0.0.1"
)

lazy val root = (project in file(".")).enablePlugins(PlayScala).settings(
  coreSettings,
  D.settings
)

fork in run := true

/*
 * UI Build Scripts
 */

val Success = 0 // 0 exit code
val Error = 1 // 1 exit code

unmanagedResourceDirectories in Assets += (baseDirectory.value / "ui" / "dist")

PlayKeys.playRunHooks <+= baseDirectory.map(UIBuild.apply)

def runScript(script: String)(implicit dir: File): Int = Process(script, dir) !

def uiWasInstalled(implicit dir: File): Boolean = (dir / "node_modules").exists()

def runNpmInstall(implicit dir: File): Int =
  if (uiWasInstalled) Success else runScript("npm install")

def ifUiInstalled(task: => Int)(implicit dir: File): Int =
  if (runNpmInstall == Success) task
  else Error

def runProdBuild(implicit dir: File): Int = ifUiInstalled(runScript("npm run build-prod"))

def runDevBuild(implicit dir: File): Int = ifUiInstalled(runScript("npm run build"))

def runUiTests(implicit dir: File): Int = ifUiInstalled(runScript("npm run test-no-watch"))

lazy val `ui-dev-build` = TaskKey[Unit]("Run UI build when developing the application.")

`ui-dev-build` := {
  implicit val UIroot = baseDirectory.value / "ui"
  if (runDevBuild != Success) throw new Exception("Oops! UI Build crashed.")
}

lazy val `ui-prod-build` = TaskKey[Unit]("Run UI build when packaging the application.")

`ui-prod-build` := {
  implicit val UIroot = baseDirectory.value / "ui"
  if (runProdBuild != Success) throw new Exception("Oops! UI Build crashed.")
}

lazy val `ui-test` = TaskKey[Unit]("Run UI tests when testing application.")

`ui-test` := {
  implicit val UIroot = baseDirectory.value / "ui"
  if (runUiTests != 0) throw new Exception("UI tests failed!")
}

`ui-test` <<= `ui-test` dependsOn `ui-dev-build`

dist <<= dist dependsOn `ui-prod-build`

stage <<= stage dependsOn `ui-prod-build`

test <<= (test in Test) dependsOn `ui-test`
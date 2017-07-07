import com.typesafe.sbt.SbtScalariform.ScalariformKeys

import scalariform.formatter.preferences._



  //version := use git describe. see https://github.com/sbt/sbt-git
  scalaVersion := V.scala
  name := "statistic"
  organization := "com.sandinh"
  version := "0.0.1"

D.settings

//see https://github.com/scala/scala/blob/2.11.x/src/compiler/scala/tools/nsc/settings/ScalaSettings.scala
scalacOptions ++= Seq("-encoding", "UTF-8"
  ,"-deprecation", "-unchecked", "-feature"
  ,"-optimise"
  ,"-Xfuture" //, "–Xverify", "-Xcheck-null"
  ,"-Ybackend:GenBCode"
  ,"-Ydelambdafy:method"
  ,"-Yinline-warnings", "-Yinline"
  ,"-Ywarn-dead-code", "-Ydead-code"
  ,"-Yclosure-elim"
  ,"-Ywarn-unused-import" //, "-Ywarn-numeric-widen"
  //`sbt doc` will fail if enable the following options!
  //,"nullary-unit", "nullary-override", "unsound-match", "adapted-args", "infer-any"
)
javacOptions ++= Seq("-encoding", "UTF-8", "-source", "1.7", "-target", "1.7", "-Xlint:unchecked", "-Xlint:deprecation")





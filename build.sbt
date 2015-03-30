organization := "com.sandinh"

name := "statistic"

version := "0.0.1"

scalaVersion := "2.11.6"

//see https://github.com/scala/scala/blob/2.11.x/src/compiler/scala/tools/nsc/settings/ScalaSettings.scala
scalacOptions ++= Seq("-encoding", "UTF-8"
  ,"-target:jvm-1.7", "-deprecation", "-unchecked", "-feature"
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

resolvers ++= Seq(
	"apache"                at "https://repository.apache.org/content/repositories/releases/",
	"Sonatype OSS Releases" at "https://oss.sonatype.org/content/repositories/releases/",
	"Typesafe Repository"   at "http://repo.typesafe.com/typesafe/releases/",
	"Big Bee Consultants"   at "http://www.bigbeeconsultants.co.uk/repo"
)

libraryDependencies ++= Seq(
  "commons-lang"      		%   "commons-lang"                    % "2.6",
  "com.sandinh"       		%%  "sd-cb"                           % "7.1.4-SNAPSHOT",
  "com.sandinh"       		%%  "play-jdbc-standalone"            % "2.1.2",
  "com.typesafe.akka"    	%%  "akka-slf4j"                      % "2.3.9",
  "mysql"                	%   "mysql-connector-java"      	    % "5.1.34",
  "org.slf4j"             %   "slf4j-api"                       % "1.7.10",
  "ch.qos.logback"        % "logback-classic"                   % "1.1.2" % "runtime",
  "joda-time"             %   "joda-time"                       % "2.5"
)
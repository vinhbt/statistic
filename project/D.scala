import play.sbt.PlayImport.{cache, jdbc, specs2}
import sbt._
import sbt.Keys._

object V {
  val scala = "2.11.8"
  val akka = "2.4.8"
}

object D {
  val deps = Seq(cache, jdbc,
    "sd.pay.card"           %% "core"                     % "3.2.6",
    "com.typesafe.akka"     %% "akka-slf4j"               % V.akka,
    "com.typesafe.play"     %% "anorm"                    % "2.5.2",
    "com.sandinh"           %% "1pay"                     % "2.2.0",
    "sd"                    %% "play-common-util"         % "1.1.0",
    "com.sandinh"           %% "php-utils"                % "1.0.5",
    "com.sandinh"           %% "sd-cb"                    % "10.4.15",
    "io.github.nremond"     %% "pbkdf2-scala"             % "0.5",
    //TODO should we update to v2-rev29-1.22.0?
    "com.google.apis"       %  "google-api-services-androidpublisher" % "v2-rev25-1.21.0",
    //@note use elastic4s 2.x.* if install elasticsearch 2.x.* on servers
    "com.sksamuel.elastic4s" %% "elastic4s-core"          % "2.3.1",
    "com.sksamuel.scrimage" %% "scrimage-core"            % "2.1.6",
    "com.typesafe.play"     %% "play-mailer"              % "5.0.0",
    "mysql"                 %  "mysql-connector-java"     % "5.1.39" % Runtime
  )

  val tests = Seq(specs2,
    "org.specs2"            %% "specs2-matcher-extra"     % "3.6.6",
    "com.sandinh"           %% "subfolder-evolutions"     % "2.5.2"
  ).map(_ % Test)

  val overrides = Set(
    "com.typesafe.akka"       %% "akka-stream"              % V.akka,
    "xalan"                   % "xalan"                     % "2.7.2",
    "org.scala-lang.modules"  %% "scala-parser-combinators" % "1.0.4",
    "org.scala-lang.modules"  %% "scala-xml"                % "1.0.5",
    "org.scala-lang"          % "scala-reflect"             % V.scala
  )

  val settings = Seq(
    resolvers ++= Seq(
      "apache"                at "https://repository.apache.org/content/repositories/releases/",
      "Sonatype OSS Releases" at "https://oss.sonatype.org/content/repositories/releases/",
      "Typesafe Repository"   at "http://repo.typesafe.com/typesafe/maven-releases/",
      "Typesafe Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
      "Big Bee Consultants"   at "http://www.bigbeeconsultants.co.uk/repo"
    ),
    libraryDependencies ++= deps ++ tests,
    dependencyOverrides ++= overrides
  )
}

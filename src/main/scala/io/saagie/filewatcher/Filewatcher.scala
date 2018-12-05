package io.saagie.filewatcher

import java.nio.file.FileSystems

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.stream.ActorMaterializer
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.{Level, Logger}
import ch.qos.logback.core.FileAppender
import com.typesafe.config.{Config, ConfigFactory}
import org.json4s._
import org.json4s.native.Serialization._
import org.slf4j.LoggerFactory

import scala.io.Source
import scala.util.Try

case class Parameters(input: Input,
                      kafka: KafkaConf,
                      log: Logging,
                      journal: Journal,
                      snapshot: Snapshot)

case class Input(paths: List[String], interval: Long, maxBufferSize: Int)
case class KafkaConf(host: List[String], topic: String)
case class Logging(dir: String, level: String)
case class ConfParameters(path: Option[String] = None)
case class Journal(dir: String)
case class Snapshot(dir: String)

case object Filewatcher extends App {
  implicit val formats = DefaultFormats

  def rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).asInstanceOf[Logger]

  val parser = new scopt.OptionParser[ConfParameters]("java -jar stream-filewatcher-<version>.jar") {
    opt[String]("conf-file")
      .abbr("c")
      .action((p, c) => c.copy(Some(p)))
      .text("Configuration (config.conf) file path.")
      .required()
  }

  def getAkkaConfig(parameters: Parameters): Config = {
    val cfg = ConfigFactory.parseString(
      s"""akka.persistence.journal.leveldb.dir=${parameters.journal.dir},
          akka.persistence.snapshot-store.local.dir=${parameters.snapshot.dir},
        """)
    val regularConfig = ConfigFactory.load
    ConfigFactory.load(cfg.withFallback(regularConfig))
  }


  def setLogConfig(parameters: Parameters): Unit = {
    rootLogger.setLevel(Level.toLevel(parameters.log.level))
    val fileAppender = rootLogger.getAppender("file").asInstanceOf[FileAppender[ILoggingEvent]]
    fileAppender.setFile(s"${parameters.log.dir}streaming-filewatcher.log")
    fileAppender.setName("file")
    rootLogger.addAppender(fileAppender)
    fileAppender.start()
  }

  parser.parse(args, ConfParameters()) match {
    case Some(conf) =>
      val parameters = read[Parameters](Source.fromFile(conf.path.get).getLines().mkString("\n"))

      this.setLogConfig(parameters)
      val cfg = this.getAkkaConfig(parameters)

      implicit val system: ActorSystem = ActorSystem(s"Filewatcher", cfg)
      implicit val materializer: ActorMaterializer = ActorMaterializer()

      val fs = FileSystems.getDefault
      val tracker: ActorRef = system.actorOf(Props(classOf[FileTracker], fs, parameters))
      val directoryWatcher = system.actorOf(Props(classOf[DirectoryWatcher], fs, tracker, parameters))
      parameters.input.paths.foreach(pathString => {
        Try {
          val path = fs.getPath(pathString)
          val parent = if (path.toFile.isDirectory) {
            ListParent(path, fs.getPathMatcher(s"regex:.*"))
          } else {
            ListParent(path.getParent, fs.getPathMatcher(s"glob:$pathString"))
          }
          directoryWatcher ! parent
        }
      })
  }
}

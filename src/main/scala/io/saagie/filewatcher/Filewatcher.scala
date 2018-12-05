package io.saagie.filewatcher

import java.nio.file.FileSystems

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.stream.ActorMaterializer
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.{Level, Logger}
import ch.qos.logback.core.FileAppender
import org.json4s._
import org.json4s.native.Serialization._
import org.slf4j.LoggerFactory

import scala.io.Source
import scala.util.Try

case class Parameters(input: Input,
                      kafka: KafkaConf,
                      log: Logging)

case class Input(paths: List[String], interval: Long, maxBufferSize: Int)

case class KafkaConf(host: List[String], topic: String)

case class Logging(dir: String, level: String)

case class ConfParameters(path: Option[String] = None)

case object Filewatcher extends App {
  implicit val system: ActorSystem = ActorSystem(s"Filewatcher")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val formats = DefaultFormats

  def rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).asInstanceOf[Logger]

  def log = LoggerFactory.getLogger(getClass)

  val parser = new scopt.OptionParser[ConfParameters]("java -jar stream-filewatcher-<version>.jar") {
    opt[String]("conf-file")
      .abbr("c")
      .action((p, c) => c.copy(Some(p)))
      .text("Configuration (config.conf) file path.")
      .required()
  }

  def manageLog(parameters: Parameters): Unit = {
    rootLogger.setLevel(Level.toLevel(parameters.log.level))
    val fileAppender = rootLogger
      .getAppender("file")
      .asInstanceOf[FileAppender[ILoggingEvent]]
    fileAppender.setFile(s"${parameters.log.dir}${fileAppender.getFile}")
  }

  parser.parse(args, ConfParameters()) match {
    case Some(conf) =>
      val parameters = read[Parameters](Source.fromFile(conf.path.get).getLines().mkString("\n"))
      this.manageLog(parameters)
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
    case None => system.terminate()
  }
}

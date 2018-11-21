package io.saagie.filewatcher

import java.nio.file.{FileSystems, Path}

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.stream.ActorMaterializer
import akka.stream.alpakka.file.DirectoryChange
import akka.stream.alpakka.file.scaladsl.{Directory, DirectoryChangesSource, FileTailSource}
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.{Level, Logger}
import ch.qos.logback.core.FileAppender
import org.json4s._
import org.json4s.native.Serialization._
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
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

  /*parser.parse(args, ConfParameters()) match {
    case Some(conf) =>
      val parameters = read[Parameters](Source.fromFile(conf.path.get).getLines().mkString("\n"))

      val fs = FileSystems.getDefault

      val tracker = system.actorOf(Props(classOf[FileTracker], fs, parameters))
      log.info("Configuration: {}", parameters)
      parameters
        .input
        .paths
        .foreach(path_conf => {
        val levels = path_conf.split("/").toSeq
        val (regex, clean) = if (levels.last.contains("*")) {
          (s"${levels.last.replace("*", "^*")}$$".r, levels.init.mkString("/"))
        } else {
          (".*".r, levels.mkString("/"))
        }
        Directory.ls(fs.getPath(clean))
          .runForeach {
            path: Path =>
              log.debug("Found in path: {}", path)
              if (path.toFile.isFile && regex.findFirstMatchIn(path.toFile.toString).isDefined) {
                tracker ! OpenFile(path.toString)
                FileTailSource
                  .lines(path, parameters.input.maxBufferSize, parameters.input.interval seconds)
                  .runForeach {
                    line =>
                      tracker ! ProcessLine(path.toString, line, parameters.kafka.topic)
                  }
              }
          }

        val source = DirectoryChangesSource(fs.getPath(clean), parameters.input.interval seconds, parameters.input.maxBufferSize)
        source.runForeach {
          case (p, change) =>
            regex.findFirstMatchIn(p.toString) match {
              case Some(_) =>
                log.info("Path: {}", p)
                log.info("Change: {}", change)
                change match {
                  case DirectoryChange.Creation =>
                    tracker ! OpenFile(p.toString)
                    FileTailSource
                      .lines(p, parameters.input.maxBufferSize, parameters.input.interval seconds)
                      .runForeach {
                        line =>
                          tracker ! ProcessLine(p.toString, line, parameters.kafka.topic)
                      }
                  case DirectoryChange.Modification =>
                    tracker ! OpenFile(p.toString)
                  case DirectoryChange.Deletion =>
                    tracker ! DeleteFile(p.toString)
                    tracker ! None
                }
              case None => log.info("Non tracker path: {}", p)
            }
          case o => log.info("No change: {}", o)
        }
      })
    case None =>
      system.terminate()
  }*/
}

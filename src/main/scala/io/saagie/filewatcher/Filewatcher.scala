package io.saagie.filewatcher

import java.nio.file.FileSystems

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.stream.ActorMaterializer
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.{Level, Logger}
import ch.qos.logback.core.FileAppender
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import scala.util.Try
import scala.collection.JavaConversions._

//case class Parameters(input: Input,
//                      kafka: KafkaConf,
//                      log: Logging,
//                      journal: Journal,
//                      snapshot: Snapshot)

//case class Input(paths: List[String], interval: Long, maxBufferSize: Int)
//case class KafkaConf(host: List[String], topic: String)
//case class Logging(dir: String, level: String)
case class ConfParameters(path: Option[String] = None)
//case class Journal(dir: String)
//case class Snapshot(dir: String)

case object Filewatcher extends App {

  def rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).asInstanceOf[Logger]

  val parser = new scopt.OptionParser[ConfParameters]("java -jar stream-filewatcher-<version>.jar") {
    opt[String]("conf-file")
      .abbr("c")
      .action((p, c) => c.copy(Some(p)))
      .text("Configuration (config.conf) file path.")
      .required()
  }


  def setLogConfig(logLevel: String, path:String): Unit = {
    rootLogger.setLevel(Level.toLevel(logLevel))
    val p = path + (if(!path.endsWith("/")) "/" else "")
    val fileAppender = rootLogger.getAppender("file").asInstanceOf[FileAppender[ILoggingEvent]]
    fileAppender.setFile(s"${p}streaming-filewatcher.log")
    fileAppender.setName("file")
    rootLogger.addAppender(fileAppender)
    fileAppender.start()
  }

    val cfg = ConfigFactory.load()
    this.setLogConfig(cfg.getString("log.dir"), cfg.getString("log.path"))

    implicit val system: ActorSystem = ActorSystem(s"Filewatcher", cfg)
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    val fs = FileSystems.getDefault
    val parameters = ???
    val tracker: ActorRef = system.actorOf(Props(classOf[FileTracker], fs, parameters))
    val directoryWatcher = system.actorOf(Props(classOf[DirectoryWatcher], fs, tracker, parameters))

    val watchPaths = cfg.getStringList("input.paths")
    watchPaths.foreach(pathString => {
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

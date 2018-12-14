package io.saagie.filewatcher

import java.io.File
import java.nio.file.FileSystems

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.stream.ActorMaterializer
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.{Level, Logger}
import ch.qos.logback.core.FileAppender
import ch.qos.logback.core.rolling.{FixedWindowRollingPolicy, RollingFileAppender, SizeBasedTriggeringPolicy, TriggeringPolicy}
import ch.qos.logback.core.util.FileSize
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory

import scala.util.Try
import scala.collection.JavaConversions._

case class ConfParameters(path: Option[String] = None)

case object Filewatcher extends App {

  def rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).asInstanceOf[Logger]

  val parser = new scopt.OptionParser[ConfParameters]("java -jar stream-filewatcher-<version>.jar") {
    opt[String]("conf-file")
      .abbr("c")
      .action((p, c) => c.copy(Some(p)))
      .text("Configuration (config.conf) file path.")
      .required()
  }

  def setLogConfig(logLevel: String, path:String, keepNbFiles:Int, maxFileSizeMB: Int): Unit = {
    rootLogger.setLevel(Level.toLevel(logLevel))
    val p = path + (if(!path.endsWith("/")) "/" else "")
    val fileAppender = rootLogger.getAppender("file").asInstanceOf[RollingFileAppender[ILoggingEvent]]
    val triggeringPolicy = fileAppender.getTriggeringPolicy.asInstanceOf[SizeBasedTriggeringPolicy[ILoggingEvent]]
    val rollingPolicy = fileAppender.getRollingPolicy.asInstanceOf[FixedWindowRollingPolicy]
    fileAppender.stop()
    triggeringPolicy.stop()
    rollingPolicy.stop()

    rollingPolicy.setFileNamePattern(s"${p}streaming-filewatcher.%i.log")
    rollingPolicy.setMaxIndex(keepNbFiles)

    triggeringPolicy.setMaxFileSize(new FileSize(maxFileSizeMB * 1024 * 1024))

    fileAppender.setFile(s"${p}streaming-filewatcher.log")
    fileAppender.setRollingPolicy(rollingPolicy)
    fileAppender.setTriggeringPolicy(triggeringPolicy)
    fileAppender.setName("file")

    rootLogger.addAppender(fileAppender)
    fileAppender.start()
    triggeringPolicy.start()
    rollingPolicy.start()
    fileAppender.start()
  }

  parser.parse(args, ConfParameters()) match {
    case Some(conf) =>
      val cfg = ConfigFactory.parseFile(new File(conf.path.get))
      this.setLogConfig(
        cfg.getString("log.level"), cfg.getString("log.dir"),cfg.getInt("log.maxIndex"), cfg.getInt("log.maxFileSizeMB")
      )

      implicit val system: ActorSystem = ActorSystem(s"Filewatcher", cfg)
      implicit val materializer: ActorMaterializer = ActorMaterializer()

      val fs = FileSystems.getDefault
      val tracker: ActorRef = system.actorOf(Props(classOf[FileTracker], fs, cfg))
      val directoryWatcher = system.actorOf(Props(classOf[DirectoryWatcher], fs, tracker, cfg))

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
}

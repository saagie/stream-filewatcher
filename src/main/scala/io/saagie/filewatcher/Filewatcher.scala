package io.saagie.filewatcher

import java.nio.file.{FileSystems, Path}

import akka.actor.{ActorSystem, Props}
import akka.stream.ActorMaterializer
import akka.stream.alpakka.file.DirectoryChange
import akka.stream.alpakka.file.scaladsl.{Directory, DirectoryChangesSource, FileTailSource}
import org.json4s._
import org.json4s.native.Serialization._

import scala.concurrent.duration._
import scala.io.Source

case class Parameters(input: Input,
                      kafka: KafkaConf)

case class Input(paths: List[String], interval: Long, maxBufferSize: Int)

case class KafkaConf(host: List[String], topic: String)

case class ConfParameters(path: Option[String] = None, verbose: Option[Boolean] = None)

case object Filewatcher extends App {
  implicit val system: ActorSystem = ActorSystem(s"Robert")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val formats = DefaultFormats

  val parser = new scopt.OptionParser[ConfParameters]("java -jar stream-filewatcher-<version>.jar") {
    opt[String]("conf-file")
      .abbr("c")
      .action((p, c) => c.copy(Some(p)))
      .text("Configuration (config.conf) file path.")
      .required()
  }

  parser.parse(args, ConfParameters()) match {
    case Some(conf) =>
      val parameters = read[Parameters](Source.fromFile(conf.path.get).getLines().mkString("\n"))

      val fs = FileSystems.getDefault

      val tracker = system.actorOf(Props(classOf[FileTracker], fs, parameters))
      println(s"$parameters")
      parameters.input.paths.foreach(path => {
        Directory.ls(fs.getPath(path))
          .runForeach {
            path: Path =>
              if (path.toFile.isFile) {
                tracker ! OpenFile(path.toString)
                FileTailSource
                  .lines(path, parameters.input.maxBufferSize, parameters.input.interval seconds)
                  .runForeach {
                    line =>
                      tracker ! ProcessLine(path.toString, line, parameters.kafka.topic)
                  }
              }
          }

        val source = DirectoryChangesSource(fs.getPath(path), parameters.input.interval seconds, parameters.input.maxBufferSize)
        source.runForeach {
          case (p, change) =>
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
                tracker ! None
              case DirectoryChange.Deletion =>
                tracker ! DeleteFile(p.toString)
                tracker ! None
            }
          case o => println(s"No change: $o")
        }
      })
    case None =>
      system.terminate()
  }
}

package io.saagie.filewatcher

import java.nio.file.{FileSystems, Path}

import akka.actor.{ActorSystem, Props}
import akka.stream.ActorMaterializer
import akka.stream.alpakka.file.DirectoryChange
import akka.stream.alpakka.file.scaladsl.{Directory, DirectoryChangesSource, FileTailSource}

import scala.concurrent.duration._

case class Parameters(path: String, interval: FiniteDuration, topic: String, maxBufferSize: Int = 8192)

object Filewatcher extends App {
  implicit val system: ActorSystem = ActorSystem(s"Robert")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val parameters = Parameters("./", 1 second, "topic")

  val fs = FileSystems.getDefault

  val tracker = system.actorOf(Props(classOf[FileTracker], fs))

  Directory.ls(fs.getPath(parameters.path))
    .runForeach { path: Path =>
      if (path.toFile.isFile) {
        tracker ! OpenFile(path.toString)
        FileTailSource
          .lines(path, parameters.maxBufferSize, parameters.interval)
          .runForeach { line =>
            tracker ! ProcessLine(path.toString, line, parameters.topic)
          }
      }
    }

  val source = DirectoryChangesSource(fs.getPath(parameters.path), parameters.interval, parameters.maxBufferSize)
  source.runForeach {
    case (p, change) =>
      change match {
        case DirectoryChange.Creation =>
          tracker ! OpenFile(p.toString)
          FileTailSource
            .lines(p, parameters.maxBufferSize, parameters.interval)
            .runForeach { line =>
              tracker ! ProcessLine(p.toString, line, parameters.topic)
            }
        case DirectoryChange.Modification =>
          tracker ! None
        case DirectoryChange.Deletion =>
          tracker ! DeleteFile(p.toString)
          tracker ! None
      }
    case o => println(s"No change: $o")
  }
}

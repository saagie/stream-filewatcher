package io.saagie.filewatcher

import java.nio.file.{FileSystem, Path, PathMatcher}

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.stream.ActorMaterializer
import akka.stream.alpakka.file.DirectoryChange
import akka.stream.alpakka.file.scaladsl.{Directory, DirectoryChangesSource}

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}
import scala.concurrent.duration._

case class ListParent(parent: Path, matcher: PathMatcher)

class DirectoryWatcher(implicit val fileSystem: FileSystem, tracker: ActorRef, implicit val parameters: Parameters) extends Actor with ActorLogging {
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  override def receive: Receive = {
    case parent: ListParent =>
      implicit val executionContext: ExecutionContextExecutor = materializer.executionContext
      val future = Directory.ls(parent.parent).runForeach(p => {
        if (p.toFile.isFile && parent.matcher.matches(p)) {
          tracker ! OpenFile(p.toString)
        }
      })
      DirectoryChangesSource(parent.parent, parameters.input.interval seconds, parameters.input.maxBufferSize)
        .runForeach {
          case (p, change) => {
            if (parent.matcher.matches(p)) {
              change match {
                case DirectoryChange.Creation =>
                  log.debug("File creation: {}", p)
                  tracker ! OpenFile(p.toString)
                case DirectoryChange.Modification =>
                case DirectoryChange.Deletion =>
                  tracker ! DeleteFile(p.toString)
                  tracker ! None
              }
            }
          }
        }
      future
        .onComplete {
          case Success(value) =>
          case Failure(exception) =>
            log.error("Exception", exception)
            materializer.system.terminate()
        }
  }
}

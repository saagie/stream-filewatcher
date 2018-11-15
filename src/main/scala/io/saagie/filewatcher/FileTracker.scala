package io.saagie.filewatcher

import java.nio.file.{FileSystem, FileSystems}

import akka.actor.ActorLogging
import akka.persistence.{PersistentActor, SnapshotOffer}
import akka.stream.ActorMaterializer
import cakesolutions.kafka.KafkaProducer.Conf
import cakesolutions.kafka.{KafkaProducer, KafkaProducerRecord}
import cats.instances.all._
import cats.kernel.Monoid
import org.apache.kafka.common.serialization.StringSerializer
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._

case class TrackerStatus(fileStates: List[FileState] = Monoid.empty[List[FileState]]) {
  def openFile(path: String): TrackerStatus = if (!fileStates.exists(_.path == path)) {
    copy(fileStates = fileStates :+ FileState(path))
  } else {
    val tracker = fileStates.find(_.path == path).get
    copy(fileStates = fileStates.filterNot(_.path == path) :+ FileState(path, Monoid.empty[Long], tracker.skip))
  }

  def deleteFile(path: String): Either[String, TrackerStatus] = if (fileStates.exists(_.path == path)) {
    Right(copy(fileStates.filterNot(_.path == path)))
  } else {
    Left("File already not tracked.")
  }

  def skipLine(path: String): TrackerStatus =
    copy(fileStates = fileStates.filter(_.path == path).head.skipLine :: fileStates.filterNot(_.path == path))

  def processLine(path: String): TrackerStatus =
    copy(fileStates = fileStates.find(_.path == path).get.processLine :: fileStates.filterNot(_.path == path))
}

case class FileState(path: String, processed: Long = Monoid.empty[Long], skip: Long = Monoid.empty[Long]) {
  def skipLine: FileState = copy(skip = skip + 1)

  def processLine: FileState = copy(processed = processed + 1)
}

class FileTracker(implicit val fileSystem: FileSystem) extends PersistentActor with ActorLogging {

  implicit val materializer: ActorMaterializer = ActorMaterializer()

  override def persistenceId: String = "file-tracker-1"

  var state = TrackerStatus()

  val snapshotInterval = 5

  val fs: FileSystem = FileSystems.getDefault

  val producer = KafkaProducer(
    Conf(new StringSerializer(), new StringSerializer(), bootstrapServers = "localhost:9092")
  )

  override def receiveRecover: Receive = {
    case open: OpenFile =>
      log.debug("Recovering file opening: {]", open)
      state = state.openFile(open.path)
    case line: SkipLine =>
      log.debug("Recovering line skip: {}", line)
      state = state.skipLine(line.path)
    case SnapshotOffer(_, snapshot: TrackerStatus) => state = snapshot
  }

  def saveSnapshot(): Unit = if (lastSequenceNr % snapshotInterval == 0 && lastSequenceNr != 0) {
    log.debug("Saving snapshot, Current state: {}", state)
    saveSnapshot(state)
  }

  override def receiveCommand: Receive = {
    case open: OpenFile => persist(open) { o =>
      state = state.openFile(o.path)
      context.system.eventStream.publish(o)
      saveSnapshot()
    }
    case delete: DeleteFile => persist(delete) { d =>
      state.deleteFile(d.path).fold(
        ex => log.error("Impossible to stop file tracking: {} cause: {}.", d.path, ex),
        st => state = st)
      context.system.eventStream.publish(d)
      saveSnapshot()
    }
    case line: SkipLine => persist(line) { l =>
      state = state.skipLine(l.path)
      context.system.eventStream.publish(l)
      saveSnapshot()
    }
    case line: ProcessLine =>
      val tracker = state.fileStates.find(_.path == line.path).get
      if (tracker.skip <= tracker.processed) {
        self ! SendToKafka(line.path, line.line, line.topic)
      }
      state = state.processLine(line.path)
    case line: SendToKafka =>
      self ! SkipLine(line.path, line.line)
      val json = compact(render(
        ("message" -> line.line) ~
          ("source" -> line.path) ~
          ("fields" -> ("log_type" -> line.topic))
      ))
      producer.send(KafkaProducerRecord(line.topic, None, json))
      log.debug("Sending line to Kafka")
    case None => println(s"Watcher Status: $state")
  }
}

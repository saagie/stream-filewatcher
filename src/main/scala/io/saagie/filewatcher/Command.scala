package io.saagie.filewatcher

trait Command {
  val path: String
}

case class OpenFile(path: String) extends Command

case class DeleteFile(path: String) extends Command

case class SkipLine(path: String, line: String) extends Command

case class ProcessLine(path: String, line: String, topic: String) extends Command

case class SendToKafka(path: String, line: String, topic: String) extends Command

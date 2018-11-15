import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.filter.LevelFilter
import ch.qos.logback.classic.filter.ThresholdFilter
import ch.qos.logback.core.spi.FilterReply

import static ch.qos.logback.classic.Level.ERROR
import static ch.qos.logback.classic.Level.WARN

appender("console", ConsoleAppender) {
    target = "System.out"
    filter(LevelFilter) {
        level = WARN
        onMatch = FilterReply.DENY
    }
    filter(LevelFilter) {
        level = ERROR
        onMatch = FilterReply.DENY
    }
    encoder(PatternLayoutEncoder) {
        pattern = "%d{yy/MM/dd HH:mm:ss.SS} %p %c{1}: %m%n"
    }
}

appender("console-err", ConsoleAppender) {
    target = "System.err"
    filter(ThresholdFilter) {
        level = WARN
    }
    encoder(PatternLayoutEncoder) {
        pattern = "%d{yy/MM/dd HH:mm:ss.SS} %p %c{1}: %m%n"
    }
}

logger("org.apache.spark", ERROR)
logger("akka.event.slf4j", WARN)
logger("org.spark-project.jetty.util.component.AbstractLifeCycle", WARN)
logger("org.apache.spark.repl.SparkIMain\$exprTyper", WARN)
logger("org.apache.spark.repl.SparkILoop\$SparkILoopInterpreter", WARN)
logger("org.apache.parquet", WARN)
logger("parquet", WARN)
logger("org.apache.hadoop", ERROR)
logger("org.apache.hadoop.hive.metastore.RetryingHMSHandler", ERROR)
logger("org.apache.hadoop.hive.ql.exec.FunctionRegistry", ERROR)
logger("org.spark_project.jetty", WARN)
logger("org.elasticsearch.hadoop", WARN)
logger("org.elasticsearch.spark", WARN)
logger("org.apache.kafka", WARN)

root(INFO, ["console", "console-err"])

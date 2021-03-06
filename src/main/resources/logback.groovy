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
appender("file", RollingFileAppender) {
    file = "/dev/null"
    rollingPolicy(FixedWindowRollingPolicy) {
        fileNamePattern = "default-streaming-filewatcher.%i.log"
        minIndex = 1
        maxIndex = 3
    }
    triggeringPolicy(SizeBasedTriggeringPolicy) {
        maxFileSize = "100MB"
    }
    encoder(PatternLayoutEncoder) {
        pattern = "%d{yy/MM/dd HH:mm:ss.SS} %p %c{1}: %m%n"
    }
}

logger("org.apache.kafka", WARN)
logger("akka.persistence", WARN)
logger("akka.event.slf4j", WARN)

root(INFO, ["console", "console-err", "file"])

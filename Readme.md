# Stream Filewatcher

This application uses [https://doc.akka.io/docs/akka/current/stream/index.html)](Akka Streams) in order to watch a 
directory and send it's information to Kafka.

### 1. Configuration

```json
{
  "input": {
    "paths": [
      "src/main/resources/*.test",
      "src/main/resources/*.test2",
      "src/main/resources/*.conf",
      "src/main/libs"
    ],
    "interval": 1,
    "maxBufferSize": 8192
  },
  "kafka": {
    "host": [
      "localhost:9092"
    ],
    "topic": "topic"
  },
  "log": {
    "dir": "/home/erwan/IdeaProjects/stream-filewatcher/",
    "level": "DEBUG"
  }
}
```
* input
    paths: The list of path to watch. Each file in the path will be read.
    interval: The interval in seconds between each file content check.
    maxBufferSize: The length of one line in the watched file.

* kafka
    host: Kafka broker list.
    topic: Kafka topic to update.

* log
    dir: Log file directory
    level: Log Level

### 2. Build

To build the application launch `gradle shadowJar` command.

### 3. Launch

To run the application, launch `java -jar stream-filewatcher.jar -c config.json`.
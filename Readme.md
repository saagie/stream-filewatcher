# Stream Filewatcher

This application uses [https://doc.akka.io/docs/akka/current/stream/index.html)](Akka Streams) in order to watch a 
directory and send it's information to Kafka.

### 1. Configuration
The configuration file has to be in the same directory as the app is launched.

```json
{
  "input": {
    "paths": [
      "./src/main/resources"
    ],
    "interval": 1,
    "maxBufferSize": 8192
  },
  "kafka": {
    "host": [
      "localhost:9092"
    ],
    "topic": "topic"
  }
}
```
* input
    paths: The list of path to watch. Each file in the path will be read.
    interval: The interval in seconds between each check of update in files.
    maxBufferSize: The length of one line in the watched file.

* kafka
    host: Kafka broker list.
    topic: Kafka topic to update.
    
### 2. Build

To build the application launch `gradle shadowJar` command.

### 3. Launch

To run the application, launch `java -jar stream-filewatcher.jar`. 
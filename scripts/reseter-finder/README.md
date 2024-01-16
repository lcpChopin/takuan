# reseter-finder

### Prerequites 

- Java 1.8+  
- Maven 3.6.3+

### Build the tool

`mvn package -DskipTests &> /dev/null`

### Example Usage

- `java -jar target/reset-finder-1.0-SNAPSHOT.jar -field "com.github.kevinsawicki.http.HttpRequest.CONNECTION_FACTORY" -klasspath /home/cgzhu/projects/iFixPlus/_downloads/http-request:/home/cgzhu/projects/iFixPlus/_downloads/http-request/lib/target/http-request-6.1-SNAPSHOT.jar`

- `java -jar target/reset-finder-1.0-SNAPSHOT.jar -field "io.elasticjob.lite.reg.zookeeper.ZookeeperRegistryCenterModifyTest.zkRegCenter" -klasspath /home/cgzhu/projects/iFixPlus/_downloads/elastic-job-lite:/home/cgzhu/projects/iFixPlus/_downloads/elastic-job-lite/elastic-job-lite-core/target/elastic-job-lite-core-3.0.0.M1-SNAPSHOT.jar`

- `java -jar target/reset-finder-1.0-SNAPSHOT.jar -fieldlist fieldlist.txt -klasspath /home/cgzhu/projects/iFixPlus/_downloads/http-request:/home/cgzhu/projects/iFixPlus/_downloads/http-request/lib/target/http-request-6.1-SNAPSHOT.jar`

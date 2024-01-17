# Apzda Audit Logger

## Usage

**1. maven import**

```xml

<dependency>
    <groupId>com.apzda.cloud</groupId>
    <artifactId>audit-client</artifactId>
</dependency>
```

**2.`@AuditLog`**

```java
import org.springframework.stereotype.Component;

@Component
public class YourClass {
    @AuditLog(activity = "test", message = "#{'you are get then id is: ' + #id +', then result is:' + #returnObj }")
    public String shouldBeAudited(String id) {
        return "hello ya:" + id;
    }
}
```

**3. `AuditLogger`**

```java

@Component
public class SomeClass {
    @Autowired
    private AuditLogger logger;

    public void someMethod() {
        logger.activity("test").message("hello world").log();
    }
} 
```

## Configuration

If you are running in Micro Service Mode without Service Discovery Server, you need following configuration:

```properties
apzda.cloud.reference.AuditService.svc-name=http://localhost:8081
```

> replace `http://localhost:8081` with the real one!

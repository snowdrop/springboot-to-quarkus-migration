## Rule: Migrate the Spring Boot parent pom to the Quarkus BOM

- **Goal**: Replace the Spring Boot plugin with the Quarkus plugin.
- **Parameters**:
  - 
- **Rule engine definition**: TODO
- **Result message**: 
  - TODO
- **Effort** _(Low, moderate, high)_: Low
- **Order** _(Rule position to help the user or tool when it should be executed)_: 
- **Reference** _(link to an existing rule)_: https://github.com/konveyor/rulesets/blob/main/default/generated/quarkus/230-springboot-parent-pom-to-quarkus.windup.yaml
- **Issue ticket**: 

## Instructions

| Instruction                                             | Command (if applicable) | Effort |
|---------------------------------------------------------|-------------------------|--------|
| Replace the Spring Boot plugin with the Quarkus plugin. | Manual edit or tool     | Low    |
| Verify pom.xml syntax and structure                     | `mvn validate`          | Low    |

## Additional information

### Spring Boot plugin (BEFORE)
```xml
<!-- Remove the Spring Boot plugin -->
<build>
    <plugins>
        <plugin>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugin>
    </plugins>
</build>
```

### Quarkus plugin (AFTER)

```xml
<!-- Add the Quarkus Maven plugin -->
<build>
    <plugins>
        <plugin>
            <groupId>io.quarkus.platform</groupId>
            <artifactId>quarkus-maven-plugin</artifactId>
            <version>${quarkus.platform.version}</version>
            <extensions>true</extensions>
            <executions>
                <execution>
                    <goals>
                        <goal>build</goal>
                        <goal>generate-code</goal>
                        <goal>generate-code-tests</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```
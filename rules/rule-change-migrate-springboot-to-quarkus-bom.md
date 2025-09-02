## Rule: Migrate the Spring Boot parent pom to the Quarkus BOM

- **Goal**: Migrate the Spring Boot parent pom (if it exists) to the Quarkus BOM which is a dependency defined as dependency management.
- **Parameters**:
  - QUARKUS_VERSION: _Quarkus target version_ Default: latest if not defined
- **Rule engine definition**: TODO
- **Result message**: 
  - Replace the Spring Parent POM with Quarkus BOM in `<dependencyManagement>` section of the application's `pom.xml` file. See: https://quarkus.io/guides/maven-tooling
- **Effort** _(Low, moderate, high)_: Low
- **Order** _(Rule position to help the user or tool when it should be executed)_: 1 
- **Reference** _(link to an existing rule)_: https://github.com/konveyor/rulesets/blob/main/default/generated/quarkus/230-springboot-parent-pom-to-quarkus.windup.yaml
- **Issue ticket**: 

## Instructions

| Instruction                                                                                                                                               | Command (if applicable) | Effort |
|-----------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------|--------|
| Remove Spring Boot parent from `<parent>` section                                                                                                         | Manual edit or tool     | Low    |
| Add Quarkus BOM to `<dependencyManagement>` section using the QUARKUS_VERSION parameter. If not defined, find the latest                                  | Manual edit or tool     | Low    |
| If no QUARKUS_VERSION is defined, find the latest released and/or supported                                                                               | Manual or tool          | low    |
| The `quarkus.platform.version` property defined par of the dependency should be declared part of the `<properties>` tag and set using the version defined | Manual edit or tool     |        |
| Verify pom.xml syntax and structure                                                                                                                       | `mvn validate`          | Low    |

## Additional information

### Spring Boot Parent POM (BEFORE)
```xml
<!-- Remove the following tag -->
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.x.x</version>
    <relativePath/>
</parent>
```

### Quarkus BOM Configuration (AFTER)
```xml
<!-- Add this to <dependencyManagement> -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.quarkus.platform</groupId>
            <artifactId>quarkus-bom</artifactId>
            <version>${quarkus.platform.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```
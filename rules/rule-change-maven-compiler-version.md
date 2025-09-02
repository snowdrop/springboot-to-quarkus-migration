## Rule: Upgrade the maven compiler version

- **Goal**: Upgrade the maven compiler version to the one supported by Quarkus 3.x
- **Parameters**:
  - JAVA_VERSION: User's Java version or default to 17
- **Rule engine definition**: TODO
- **Result message**: 
  - TODO
- **Effort** _(Low, moderate, high)_: Low
- **Order** _(Rule position to help the user or tool when it should be executed)_: 
- **Reference** _(link to an existing rule)_: https://github.com/konveyor/rulesets/blob/main/default/generated/quarkus/230-springboot-parent-pom-to-quarkus.windup.yaml
- **Issue ticket**: 

## Instructions

| Instruction                                                                                                                      | Command (if applicable) | Effort |
|----------------------------------------------------------------------------------------------------------------------------------|-------------------------|--------|
| Update Maven compiler plugin to the target version specified with JAVA_VERSION, otherwise use the default needed for Quarkus 3.x | Manual edit or tool     | Low    |
| Verify pom.xml syntax and structure                                                                                              | `mvn validate`          | Low    |

## Additional information

### Update <properties> (BEFORE)
```xml
<properties>
    <!-- maven.compiler version not defined -->
</properties>

or 

<properties>
    <maven.compiler.source>dd</maven.compiler.source>
    <maven.compiler.target>dd</maven.compiler.target>
</properties>
```

### Update <properties> (AFTER)
```xml
<properties>
    <maven.compiler.source>17</maven.compiler.source>
    <maven.compiler.target>17</maven.compiler.target>
</properties>
```
## Rule: Set the Quarkus version to the properties

- **Goal**: Set the Quarkus version to the `<properties/>` tag
- **Parameters**:
  - QUARKUS_VERSION: _Quarkus target version_ Default: latest if not defined
- **Rule engine definition**: TODO
- **Result message**: 
  - TODO See: https://quarkus.io/guides/maven-tooling
- **Effort** _(Low, moderate, high)_: Low
- **Order** _(Rule position to help the user or tool when it should be executed)_: 1 
- **Reference** _(link to an existing rule)_: https://github.com/konveyor/rulesets/blob/main/default/generated/quarkus/230-springboot-parent-pom-to-quarkus.windup.yaml
- **Issue ticket**: 

## Instructions

| Instruction                                                                                                                                                   | Command (if applicable) | Effort |
|---------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------|--------|
| The `quarkus.platform.version` property defined part of the `<dependency>` should be declared within the `<properties>` tag and set using the version defined | Manual edit or tool     |        |
| if no QUARKUS_VERSION variable is define, then fetch the latest 3.x release and supported                                                                     |                         |        |
| Verify pom.xml syntax and structure                                                                                                                           | `mvn validate`          | Low    |

## Additional information

### Update <properties> (BEFORE)
```xml
<properties>
    <!-- quarkus.platform.version version not defined -->
</properties>
```

### Update <properties> (AFTER)
```xml
<properties>
    <quarkus.platform.version>3.x.x</quarkus.platform.version>
</properties>
```
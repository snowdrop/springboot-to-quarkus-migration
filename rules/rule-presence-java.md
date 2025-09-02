## Rule: Presence of a JDK on the user's machine

- **Goal**: Verify the presence of a (supported) JDK on the user's machine for a specific version.
- **Parameters**:
  - JAVA_VERSION: _JDK version to be controlled locally_
- **Rule engine definition**: TODO
- **Result message**: 
  - SUCCEEDED: The correct JDK is well installed on your local machine.
  - FAILED: No JDK is available locally or don't match the target version.
- **Effort** _(Low, moderate, high)_:
- **Order** _(Rule position to help the user or tool when it should be executed)_: 
- **Reference** _(link to an existing rule)_:
- **Issue ticket**: 

## Instructions

| Instruction                                      | Command (if applicable) | Effort |
|--------------------------------------------------|-------------------------|--------|
| Check locally which java version is installed ?  | "java -version"         | Low    |
| Check locally which maven version is installed ? | "mvn --version"         | Low    |

## Additional information

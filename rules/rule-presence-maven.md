## Rule: Presence of the Apache Maven tool on the user's machine

- **Goal**: Verify the presence of the Apache Maven tool on the user's machine
- **Parameters**:
  - MAVEN_VERSION: _Apache Maven version to be controlled locally_
- **Rule engine definition**: TODO
- **Result message**: 
  - SUCCEEDED: Apache Maven is well installed on your local machine.
  - FAILED: No Apache Maven is available locally or don't match the target version.
- **Effort** _(Low, moderate, high)_:
- **Order** _(Rule position to help the user or tool when it should be executed)_: 
- **Reference** _(link to an existing rule)_:
- **Issue ticket**: 

## Instructions

| Instruction                                      | Command (if applicable) | Effort |
|--------------------------------------------------|-------------------------|--------|
| Check locally which maven version is installed ? | "mvn --version"         | Low    |

## Additional information

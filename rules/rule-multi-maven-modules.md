## Rule: Maven multi-modules application

- **Goal**: Check if the project of the code source is an Apache Maven multi-modules application
- **Parameters**: N/A
- **Rule engine definition**: TODO
- **Result message**: 
  - TRUE: This is a multi-modules project that currently we don't support. It is nevertheless possible to perform manually some steps of the migration process.
  - FALSE: This is a standalone maven project. Let's continue then.
- **Effort** _(low, moderate, hign)_: low
- **Order** _(Rule position to help the user or tool when it should be executed)_: 
- **Reference** _(link to an existing rule)_:
- **Issue ticket**: 

## Instructions

| Instruction                                                         | Command (if applicable) | Effort |
|---------------------------------------------------------------------|-------------------------|--------|
| Open the pom.xml and check if it includes the xml tag `<modules>` ? |          | Low    |

## Additional information

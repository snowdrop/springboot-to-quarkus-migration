## Rule: Compatibility of the existing license with Apache

- **Goal**: Check the compatibility of the existing license with Apache License 2.
- **Rule engine definition**: TODO
- **Result message**: 
  - SUCCEEDED: Your Spring Boot project uses [Apache License 2.0 | MIT | BSD licenses] and by consequence  can be migrated.
  - FAILED: As your license is [GPL|proprietary], consult your legal counsel before proceeding
- **Effort** _(Low, moderate, high)_:
- **Order** _(Rule position to help the user or tool when it should be executed)_: 
- **Reference** _(link to an existing rule)_:
- **Issue ticket**: 

## Instructions

| Instruction                                                                 | Command (if applicable) | Effort |
|-----------------------------------------------------------------------------|-------------------------|--------|
| Verify that the licensing model of your project is compatible with Apache 2 |          | Low    |

## Additional information

Common Compatible Licenses

✅ Permissive licenses (generally compatible):
- Apache License 2.0
- MIT License
- BSD License (2-clause, 3-clause)
- ISC License

⚠️ Copyleft licenses (require careful review):
- GPL v2/v3 (may have compatibility issues)
- LGPL (usually compatible for linking)
- Mozilla Public License 2.0

❌ Incompatible licenses:
- Proprietary/commercial licenses with redistribution restrictions
- Some GPL variants with strict copyleft requirements

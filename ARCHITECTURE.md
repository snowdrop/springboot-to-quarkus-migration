## Architecture design

### Requirements

### Challenges

The current challenge without having too much spaghetti code is to be able to perform what it is described hereafter using openrewrite OR openrewrite AND Language Server = AST that we can query to find annotation, class, import, method.

During the "analysis phase" of an application that we query to find according to different rules = condition to match: annotation, class, method, import, etc, we will calculate different actions to be done and will generate a report containing a list of actions or steps to be done where a step could match 1 or many recipes depending on the logic defined part of the recipe :

- issue 1: "name" - "description" - "id" - "link" etc:
    - step 1.1 - Replace the Spring Parent from pom.xml with Quarkus Bom (https://github.com/snowdrop/springboot-to-quarkus-migration?tab=readme-ov-file#replace-the-spring-boot-parent-with-the-quarkus-bom) -1 recipe 1
    - step 1.2 - Set the Quarkus version (https://github.com/snowdrop/springboot-to-quarkus-migration?tab=readme-ov-file#set-the-quarkus-version) -> recipe 2
- issue 2: "name" - "description" - "id" - "link" etc:
    - step 2.1 - Replace the Spring Boot plugin with Quarkus maven plugin (https://github.com/snowdrop/springboot-to-quarkus-migration?tab=readme-ov-file#replace-the-spring-boot-plugin-with-the-quarkus-one) -> recipe 3
      ...
      Remark: The generated list will also include some steps difficult to be fixed using a recipe and suggestions will be made to the user to perform the step manually or using another tool or AI or ...
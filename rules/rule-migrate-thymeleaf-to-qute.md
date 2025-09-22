## Rule: Migrate Thymeleaf templates to Qute

- **Goal**: Migrate Thymeleaf HTML templates to Qute templating engine with server-side object jdtLSConfiguration
- **Parameters**:
  - TEMPLATE_DIRECTORY: _Directory containing Thymeleaf templates (default: src/main/resources/templates)_
  - CONTROLLER_PACKAGE: _Package containing Spring @Controller classes_
- **Rule engine definition**: TODO
- **Result message**: 
  - SUCCEEDED: Thymeleaf templates successfully migrated to Qute with proper object binding
  - FAILED: Template migration failed due to syntax incompatibilities or missing dependencies
- **Effort** _(Low, moderate, high)_: Moderate
- **Order** _(Rule position to help the user or tool when it should be executed)_: After dependency migration
- **Reference** _(link to an existing rule)_: https://quarkus.io/guides/qute-reference
- **Issue ticket**: 

## Instructions

| Instruction                                                    | Command (if applicable)                    | Effort   |
|----------------------------------------------------------------|--------------------------------------------|----------|
| Add Qute dependency to pom.xml                                | Manual edit or migration tool             | Low      |
| Convert Thymeleaf syntax to Qute syntax                       | Find/replace with manual verification     | Moderate |
| Update controller annotations from @Controller to @Path       | Manual refactoring                         | Moderate |
| Replace @RequestMapping with @GET/@POST and @Template         | Manual refactoring                         | Moderate |
| Update object binding from Model to template data objects     | Manual refactoring                         | Low      |
| Test template rendering with server-side objects              | Run application and verify pages          | Low      |

## Additional information

### Syntax Migration Examples

**Thymeleaf to Qute Conversion:**

```html
<!-- Thymeleaf -->
<div th:text="${user.name}">Default Name</div>
<div th:if="${user.active}">Active User</div>
<ul>
  <li th:each="item : ${items}" th:text="${item.title}">Item</li>
</ul>

<!-- Qute -->
<div>{user.name}</div>
{#if user.active}Active User{/if}
<ul>
  {#for item in items}
  <li>{item.title}</li>
  {/for}
</ul>
```

### Controller Migration

**Spring Controller:**
```java
@Controller
public class UserController {
    @RequestMapping("/user")
    public String user(Model model) {
        model.addAttribute("user", userService.getCurrentUser());
        return "user";
    }
}
```

**Quarkus Controller:**
```java
@Path("/user")
public class UserController {
    @GET
    @Template
    public TemplateInstance user() {
        return Templates.user()
            .data("user", userService.getCurrentUser());
    }
}
```

### Required Dependencies

Add to pom.xml:
```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-qute</artifactId>
</dependency>
```

### Common Migration Challenges

- **Fragment inclusion**: Thymeleaf `th:include` → Qute `{#include}`
- **Conditional rendering**: Thymeleaf `th:if/unless` → Qute `{#if}/{#unless}`
- **Form handling**: Spring form binding → Quarkus form processing
- **Internationalization**: Thymeleaf `#{message}` → Qute message bundles

### Verification Steps

1. Ensure all template files render without errors
2. Verify object data binding works correctly
3. Test form submissions and data processing
4. Validate conditional logic and loops
5. Check fragment inclusion and template composition
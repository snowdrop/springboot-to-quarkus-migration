package dev.snowdrop.openrewrite;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import org.eclipse.lsp4j.SymbolKind;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;

import java.lang.reflect.Type;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class JdtToOpenRewrite {

    private static final String JDT_LS_JSON_RESPONSE = """
    [
      {
        "name": "org.springframework.boot.autoconfigure.SpringBootApplication",
        "kind": 5,
        "location": {
          "uri": "file:///Users/cmoullia/code/application-modernisation/spring-to-quarkus-guide/java-analyzer-quarkus/applications/spring-boot-todo-app/src/main/java/com/todo/app/AppApplication.java",
          "range": {
            "start": { "line": 3.0, "character": 7.0 },
            "end": { "line": 3.0, "character": 67.0 }
          }
        },
        "containerName": ""
      }
    ]
    """;

    // --- A record to model the structure of the JSON response, now with SymbolKind ---
    public record JdtLocation(String uri, Object range) {}
    public record JdtResult(String name, SymbolKind kind, JdtLocation location, String containerName) {}

    public static void main(String[] args) {

        // Create a GsonBuilder and register our custom deserializer for SymbolKind
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(SymbolKind.class, new SymbolKindDeserializer());
        Gson gson = gsonBuilder.create();

        Type resultListType = new TypeToken<List<JdtResult>>() {}.getType();
        List<JdtResult> jdtResults = gson.fromJson(JDT_LS_JSON_RESPONSE, resultListType);

        if (jdtResults.isEmpty()) {
            System.out.println("No annotations found by JDT-LS.");
            return;
        }

        JdtResult target = jdtResults.get(0);
        System.out.println("JDT-LS found symbol '" + target.name() + "' with kind: " + target.kind());


        // --- 3. RUN THE TRANSFORMATION ---
        // We take the first result as our target for this example
        runOpenRewriteTransformation(target);
    }

    private static void runOpenRewriteTransformation(JdtResult jdtResult) {
        String sourceCode;
        try {
            // --- DYNAMICALLY READ THE FILE CONTENT FROM THE URI ---
            URI fileUri = new URI(jdtResult.location().uri());
            Path filePath = Paths.get(fileUri);

            // Read the content from the file path identified by JDT-LS
            sourceCode = Files.readString(filePath);
            System.out.println("\nSuccessfully read content from: " + filePath);

        } catch (Exception e) {
            System.err.println("Failed to read or create source file from URI: " + jdtResult.location().uri());
            e.printStackTrace();
            return; // Exit if we can't get the source code
        }

        System.out.println("--- SOURCE CODE BEFORE TRANSFORMATION ---");
        System.out.println(sourceCode);
        System.out.println("-----------------------------------------\n");

        // --- 4. PREPARE AND APPLY THE OPENREWRITE RECIPE ---

        // The recipe will specifically target the annotation found by JDT-LS
        Recipe recipe = new ChangeSpringBootToQuarkusRecipe(jdtResult.name());
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        // Parse the source file into OpenRewrite's format (LST)
        J.CompilationUnit cu = JavaParser.fromJavaVersion()
            .build()
            .parse(ctx, sourceCode)
            .findFirst()
            .map(J.CompilationUnit.class::cast)
            .orElseThrow(() -> new IllegalArgumentException("Could not parse source code."));

        // Run the recipe on the parsed code
        J.CompilationUnit transformedCu = (J.CompilationUnit) recipe.getVisitor().visit(cu, ctx);

        if (transformedCu == cu) {
            System.out.println("Recipe did not make any changes.");
        } else {
            // --- 5. SHOW THE RESULT ---
            String transformedCode = transformedCu.printAll();
            System.out.println("--- SOURCE CODE AFTER TRANSFORMATION ---");
            System.out.println(transformedCode);
            System.out.println("----------------------------------------");
        }
    }
}

/**
 * A custom Gson deserializer to convert a number from JSON into a SymbolKind enum.
 */
class SymbolKindDeserializer implements JsonDeserializer<SymbolKind> {
    @Override
    public SymbolKind deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        int kindValue = json.getAsInt();
        return SymbolKind.forValue(kindValue);
    }
}

/**
 * A custom OpenRewrite Recipe to change a specific annotation.
 */
class ChangeSpringBootToQuarkusRecipe extends Recipe {
    private final String annotationToChange;

    ChangeSpringBootToQuarkusRecipe(String annotationToChange) {
        this.annotationToChange = annotationToChange;
    }

    @Override
    public String getDisplayName() {
        return "Change Spring Boot to Quarkus Main Annotation";
    }

    @Override
    public String getDescription() {
        return "Replaces @SpringBootApplication with @QuarkusMain and removes the old import.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                J.CompilationUnit c = super.visitCompilationUnit(cu, ctx);

                // Check if we made any annotation changes and handle imports accordingly
                boolean hasSpringBootAnnotation = cu.printAll().contains("@SpringBootApplication");
                if (hasSpringBootAnnotation) {
                    maybeRemoveImport("org.springframework.boot.autoconfigure.SpringBootApplication");
                    maybeRemoveImport("org.springframework.boot.SpringApplication");
                    maybeAddImport("io.quarkus.runtime.annotations.QuarkusMain");
                    maybeAddImport("io.quarkus.runtime.Quarkus");
                }

                return c;
            }

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation a = super.visitAnnotation(annotation, ctx);
                if(annotationToChange.contains(a.getSimpleName())) {
                    return JavaTemplate.builder("@QuarkusMain")
                        .javaParser(JavaParser.fromJavaVersion().classpath("quarkus-core"))
                        .imports("io.quarkus.runtime.annotations.QuarkusMain")
                        .build()
                        .apply(getCursor(), a.getCoordinates().replace());
                }
                return a;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);

                // Check if this is SpringApplication.run()
                if (m.getSelect() != null &&
                    m.getSelect().toString().equals("SpringApplication") &&
                    m.getSimpleName().equals("run")) {

                    // Replace the code with Quarkus run
                    return JavaTemplate.builder("Quarkus.run(#{any(java.lang.Class)}, #{any(java.lang.String[])})")
                        .javaParser(JavaParser.fromJavaVersion().classpath("quarkus-core"))
                        .imports("io.quarkus.runtime.Quarkus")
                        .build()
                        .apply(getCursor(), m.getCoordinates().replace(), m.getArguments().get(0), m.getArguments().get(1));
                }
                return m;
            }
        };
    }
}
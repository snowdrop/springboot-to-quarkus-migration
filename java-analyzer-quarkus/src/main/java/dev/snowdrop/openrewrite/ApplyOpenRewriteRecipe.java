package dev.snowdrop.openrewrite;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import dev.snowdrop.openrewrite.model.Result;
import org.eclipse.lsp4j.SymbolKind;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;

import java.lang.reflect.Type;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class ApplyOpenRewriteRecipe {

    private static Instant start;
    private static Instant finish;

    public static void main(String[] args) {

        String javaFilePath = Optional
            .ofNullable(System.getProperty("JAVA_FILE_PATH"))
            .orElse("/Users/cmoullia/code/application-modernisation/spring-to-quarkus-guide/java-analyzer-quarkus/applications/spring-boot-todo-app/src/main/java/com/todo/app/AppApplication.java");

        String JDT_LS_JSON_RESPONSE = String.format("""
            [
              {
                "name": "org.springframework.boot.autoconfigure.SpringBootApplication",
                "kind": 5,
                "location": {
                  "uri": "file://%s",
                  "range": {
                    "start": { "line": 3.0, "character": 7.0 },
                    "end": { "line": 3.0, "character": 67.0 }
                  }
                },
                "containerName": ""
              }
            ]""", javaFilePath);

        start = Instant.now();

        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(SymbolKind.class, new SymbolKindDeserializer());
        Gson gson = gsonBuilder.create();

        Type resultListType = new TypeToken<List<Result>>() {
        }.getType();
        List<Result> results = gson.fromJson(JDT_LS_JSON_RESPONSE, resultListType);

        if (results.isEmpty()) {
            System.out.println("No annotations found by JDT-LS.");
            return;
        }

        Result target = results.get(0);
        System.out.println("JDT-LS found symbol '" + target.name() + "' with kind: " + target.kind());

        runOpenRewriteTransformation(target);
    }

    private static void runOpenRewriteTransformation(Result result) {
        String sourceCode;
        try {
            URI fileUri = new URI(result.location().uri());
            Path filePath = Paths.get(fileUri);

            // Read the content from the file path identified by JDT-LS
            sourceCode = Files.readString(filePath);
            System.out.println("\nSuccessfully read content from: " + filePath);

        } catch (Exception e) {
            System.err.println("Failed to read or create source file from URI: " + result.location().uri());
            e.printStackTrace();
            return; // Exit if we can't get the source code
        }

        System.out.println("--- SOURCE CODE BEFORE TRANSFORMATION ---");
        System.out.println(sourceCode);
        System.out.println("-----------------------------------------\n");

        Recipe recipe = new SpringBootToQuarkusRecipe(result.name());
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        // Parse the source file into OpenRewrite's format (LST)
        J.CompilationUnit cu = JavaParser.fromJavaVersion()
            .build()
            .parse(ctx, sourceCode)
            .findFirst()
            .map(J.CompilationUnit.class::cast)
            .orElseThrow(() -> new IllegalArgumentException("Could not parse source code."));

        J.CompilationUnit transformedCu = (J.CompilationUnit) recipe.getVisitor().visit(cu, ctx);

        if (transformedCu == cu) {
            System.out.println("Recipe did not make any changes.");
        } else {
            String transformedCode = transformedCu.printAll();
            System.out.println("--- SOURCE CODE AFTER TRANSFORMATION ---");
            System.out.println(transformedCode);
            System.out.println("----------------------------------------");
        }

        finish = Instant.now();
        long timeElapsed = Duration.between(start, finish).toMillis();
        System.out.println("--- Elapsed time: " + timeElapsed + " ms ---");

    }
}


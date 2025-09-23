package dev.snowdrop.openrewrite;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;

public class SpringBootToQuarkusRecipe extends Recipe {
    private final String annotationToChange;

    SpringBootToQuarkusRecipe(String annotationToChange) {
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
                if (annotationToChange.contains(a.getSimpleName())) {
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

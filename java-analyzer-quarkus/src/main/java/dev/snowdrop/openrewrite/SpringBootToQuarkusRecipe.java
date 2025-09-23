package dev.snowdrop.openrewrite;

import org.openrewrite.*;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

public class SpringBootToQuarkusRecipe extends Recipe {

    @Override
    public String getDisplayName() {
        return "Convert @SpringBootApplication with @QuarkusMain Annotation";
    }

    @Override
    public String getDescription() {
        return "Convert @SpringBootApplication with @QuarkusMain Annotation, removes the old import and add the new one.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
            Preconditions.or(
                new UsesType<>("org.springframework.boot.autoconfigure.SpringBootApplication", null)
            ),
            new SpringBootToQuarkusMainVisitor());
    }

    private static class SpringBootToQuarkusMainVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final AnnotationMatcher SPRINGBOOT_APPLICATION_MATCHER = new AnnotationMatcher("@org.springframework.boot.autoconfigure.SpringBootApplication");

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx){
            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

            boolean hasSpringBootApplication = false;

            for (J.Annotation annotation : classDecl.getLeadingAnnotations()) {
                if (SPRINGBOOT_APPLICATION_MATCHER.matches(annotation)) {
                    hasSpringBootApplication = true;
                }
            }

            if (hasSpringBootApplication) {
                maybeAddImport("io.quarkus.runtime.annotations.QuarkusMain");
                return JavaTemplate.builder("@QuarkusMain(\"\")")
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "quarkus-core"))
                    .imports("io.quarkus.runtime.annotations.QuarkusMain")
                    .build()
                    .apply(getCursor(), cd.getCoordinates().addAnnotation((a1, a2) -> 0));
            }

            return cd;
        }

/*        @Override
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
        }*/
    }
}

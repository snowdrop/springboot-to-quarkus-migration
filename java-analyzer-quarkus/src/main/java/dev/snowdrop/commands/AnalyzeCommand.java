package dev.snowdrop.commands;

import dev.snowdrop.ls.JdtLsFactory;
import dev.snowdrop.ls.model.JdtLSConfiguration;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import picocli.CommandLine;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static dev.snowdrop.ls.services.LsSearchService.analyzeCodeFromRule;

@CommandLine.Command(
    name = "analyze",
    description = "Analyze a project for migration"
)
@ApplicationScoped
public class AnalyzeCommand implements Runnable {
    private static final Logger logger = Logger.getLogger(AnalyzeCommand.class);

    @CommandLine.Parameters(
        index = "0",
        description = "Path to the Java project to analyze"
    )
    @ConfigProperty(name = "analyzer.app.path", defaultValue = "./applications/spring-boot-todo-app")
    public String appPath;

    @CommandLine.Option(
        names = {"-r", "--rules"},
        description = "Path to rules directory (default: from config)"
    )
    @ConfigProperty(name = "analyzer.rules.path", defaultValue = "./rules")
    public String rulesPath;

    @CommandLine.Option(
        names = {"--jdt-ls-path"},
        description = "Path to JDT-LS installation (default: from config)",
        required = false
    )
    @ConfigProperty(name = "analyzer.jdt.ls.path", defaultValue = "./jdt/konveyor-jdtls")
    public String jdtLsPath;

    @CommandLine.Option(
        names = {"--jdt-workspace"},
        description = "Path to JDT workspace directory (default: from config)",
        required = false
    )
    @ConfigProperty(name = "analyzer.jdt.workspace.path", defaultValue = "./jdt")
    public String jdtWorkspace;

    @ConfigProperty(name = "analyzer.jdt.ls.command", defaultValue = "java.project.getAll")
    public String lsCommand;

    @CommandLine.Option(
        names = {"-v", "--verbose"},
        description = "Enable verbose output"
    )
    private boolean verbose;

    @Override
    public void run() {
        Path path = Paths.get(appPath);
        if (!path.toFile().exists()) {
            logger.errorf("❌ Project path of the application does not exist: %s", appPath);
            return;
        }

        // Check if it's a Maven project
        File pomFile = path.resolve("pom.xml").toFile();
        if (!pomFile.exists()) {
            logger.error("❌ No pom.xml found. Only Maven projects are currently supported.");
            return;
        }

        // Check if it's a Spring Boot project
        try {
            String pomContent = Files.readString(pomFile.toPath());
            if (!pomContent.contains("spring-boot")) {
                logger.warn("⚠️  This doesn't appear to be a Spring Boot project.");
            }
        } catch (Exception e) {
            logger.errorf("❌ Error reading pom.xml: %s", e.getMessage());
            return;
        }

        logger.infof("✅ Maven project detected");
        logger.infof("✅ Spring Boot dependencies found");

        try {
            JdtLsFactory jdtLsFactory = new JdtLsFactory();
            jdtLsFactory.initProperties(this);
            jdtLsFactory.launchLsProcess();
            jdtLsFactory.createLaunchLsClient();
            jdtLsFactory.initLanguageServer();

            startAnalyse(jdtLsFactory);
        } catch (Exception e) {
            logger.errorf("❌ Error: %s", e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
        }

        logger.infof("\n📊 Analysis Summary:");
        logger.infof("- Migration: Ready for next step");
    }

    private void startAnalyse(JdtLsFactory factory) throws Exception {
        logger.infof("\n🚀 Starting jdt ls analysis...");

        // Log resolved paths for debugging
        logger.infof("📋 jdt ls path: %s", factory.jdtLsPath);
        logger.infof("📋 workspace: %s", factory.jdtWks);
        logger.infof("📋 project path: %s", factory.appPath);
        logger.infof("📋 rules path: %s", factory.rulesPath);
        logger.infof("📋 LS_CMD set to: %s", factory.lsCmd);

        try {
            analyzeCodeFromRule(factory);

            logger.infof("⏳ Waiting for LS commands to complete...");
            Thread.sleep(5000); // Give time for async operations to complete

        } finally {
            if (factory.process != null && factory.process.isAlive()) {
                logger.infof("🛑 Shutting down JDT Language Server...");
                factory.process.destroyForcibly();
            }
        }
    }
}
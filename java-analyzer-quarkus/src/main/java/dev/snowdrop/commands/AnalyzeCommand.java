package dev.snowdrop.commands;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.snowdrop.ls.JdtLsFactory;
import dev.snowdrop.ls.model.JdtLSConfiguration;
import dev.snowdrop.ls.utils.FileUtils;
import dev.snowdrop.ls.utils.LSClient;
import dev.snowdrop.ls.utils.RuleUtils;
import dev.snowdrop.ls.services.LsSearchService;
import dev.snowdrop.ls.model.Rule;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import picocli.CommandLine;
import org.eclipse.lsp4j.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageServer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static dev.snowdrop.ls.utils.FileUtils.resolvePath;

@CommandLine.Command(
    name = "analyze",
    description = "Analyze a project for migration"
)
@ApplicationScoped
public class AnalyzeCommand implements Runnable {

    private static final Logger logger = Logger.getLogger(AnalyzeCommand.class);

    @Inject
    JdtLSConfiguration jdtLSConfiguration;

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
    String rulesPath;

    @CommandLine.Option(
        names = {"--jdt-ls-path"},
        description = "Path to JDT-LS installation (default: from config)",
        required = false
    )
    @ConfigProperty(name = "analyzer.jdt.ls.path", defaultValue = "./jdt/konveyor-jdtls")
    String jdtLsPath;

    @CommandLine.Option(
        names = {"--jdt-workspace"},
        description = "Path to JDT workspace directory (default: from config)",
        required = false
    )
    @ConfigProperty(name = "analyzer.jdt.workspace.path", defaultValue = "./jdt")
    String jdtWorkspace;

    @ConfigProperty(name = "analyzer.jdt.ls.command", defaultValue = "java.project.getAll")
    String lsCommand;

    @CommandLine.Option(
        names = {"-o", "--output"},
        description = "Output format: text, json (default: text)"
    )
    private String outputFormat = "text";

    @CommandLine.Option(
        names = {"-v", "--verbose"},
        description = "Enable verbose output"
    )
    private boolean verbose;

    private static final long TIMEOUT = 30000;

    private Process jdtProcess = null;
    private LanguageServer remoteProxy;
    private Launcher<LanguageServer> launcher;

    @Override
    public void run() {
        // Validate project path
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
            jdtLsFactory.initProperties();
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
        logger.infof("\n🚀 Starting JDT Language Server analysis...");
        logger.infof("📋 Configuration: JDT-LS path: %s, JDT Workspace: %s, Rules: %s & Application to scan: %s", jdtLsPath, jdtWorkspace, rulesPath, appPath);

        // Validate input values
        if (jdtLsPath == null) {
            throw new Exception("JDT-LS path is null");
        }
        if (jdtWorkspace == null) {
            throw new Exception("JDT workspace path is null");
        }
        if (rulesPath == null) {
            throw new Exception("Rules path is null");
        }
        if (appPath == null) {
            throw new Exception("Project path is null");
        }

        // Resolve paths before setting system properties
        String resolvedJdtLsPath = resolvePath(jdtLsPath).toString();
        String resolvedJdtWorkspace = resolvePath(jdtWorkspace).toString();
        String resolvedProjectPath = resolvePath(appPath.toString()).toString();
        String resolvedRulesPath = resolvePath(rulesPath).toString();

        // Log resolved paths for debugging
        logger.infof("📋 JDT-LS path: %s", resolvedJdtLsPath);
        logger.infof("📋 workspace: %s", resolvedJdtWorkspace);
        logger.infof("📋 project path: %s", resolvedProjectPath);
        logger.infof("📋 rules path: %s", resolvedRulesPath);
        logger.infof("📋 LS Command: %s", lsCommand);

        System.setProperty("JDT_LS_PATH", resolvedJdtLsPath);
        System.setProperty("JDT_WKS", resolvedJdtWorkspace);
        System.setProperty("APP_PATH", resolvedProjectPath);
        System.setProperty("RULES_PATH", resolvedRulesPath);
        System.setProperty("LS_CMD", lsCommand);

        logger.infof("📋 LS_CMD set to: %s", factory.lsCmd);

        try {
/*            logger.infof("🚀 Starting JDT Language Server process...");
            launchJdtProcess();

            logger.infof("🔌 Connecting to JDT Language Server...");
            createLaunchLsClient();

            remoteProxy = launcher.getRemoteProxy();

            logger.infof("⚙️  Initializing JDT Language Server...");
            CompletableFuture<InitializeResult> future = initializeLanguageServer(appPath);*/


            List<Rule> rules = RuleUtils.loadRules();

            if(rules.isEmpty()) {
                logger.infof("⚠️  No rules found, skipping JDT-LS analysis");
                return;
            }

            logger.infof("🔍 Executing %s rules...", rules.size());
            for (Rule rule : rules) {
                logger.infof("\n🔍 Executing rule: %s", rule.ruleID());
                executeRule(factory, rule);
            }

            logger.infof("⏳ Waiting for LS commands to complete...");
            Thread.sleep(5000); // Give time for async operations to complete

        } finally {
            if (jdtProcess != null && jdtProcess.isAlive()) {
                logger.infof("🛑 Shutting down JDT Language Server...");
                jdtProcess.destroyForcibly();
            }
        }
    }

    private void executeRule(JdtLsFactory factory, Rule rule) {
        try {
            if (verbose) {
                logger.infof("  Rule description: %s", rule.message());
                logger.infof("  Effort: %s", rule.effort());
            }

            logger.infof("  📋 Executing rule: %s", rule.message());

            // Check if rule has any java.referenced conditions (single, OR, or AND)
            boolean hasJavaReferenced = false;
            if (rule.when() != null) {
                if (rule.when().javaReferenced() != null) {
                    logger.infof("  🔍 Single condition - Pattern: %s", rule.when().javaReferenced().pattern());
                    logger.infof("  📍 Location: %s", rule.when().javaReferenced().location());
                    hasJavaReferenced = true;
                } else if (rule.when().or() != null && !rule.when().or().isEmpty()) {
                    logger.infof("  🔍 OR conditions found: %s conditions", rule.when().or().size());
                    for (int i = 0; i < rule.when().or().size(); i++) {
                        var condition = rule.when().or().get(i);
                        if (condition.javaReferenced() != null) {
                            logger.infof("    OR[%s] Pattern: %s", i, condition.javaReferenced().pattern());
                            logger.infof("    OR[%s] Location: %s", i, condition.javaReferenced().location());
                        }
                    }
                    hasJavaReferenced = true;
                } else if (rule.when().and() != null && !rule.when().and().isEmpty()) {
                    logger.infof("  🔍 AND conditions found: %s conditions", rule.when().and().size());
                    for (int i = 0; i < rule.when().and().size(); i++) {
                        var condition = rule.when().and().get(i);
                        if (condition.javaReferenced() != null) {
                            logger.infof("    AND[%s] Pattern: %s", i, condition.javaReferenced().pattern());
                            logger.infof("    AND[%s] Location: %s", i, condition.javaReferenced().location());
                        }
                    }
                    hasJavaReferenced = true;
                }
            }

            if (hasJavaReferenced) {
                // Check if JDT process is still alive before executing LS command
                if (jdtProcess != null && jdtProcess.isAlive()) {
                    logger.infof("  🚀 Executing LS command (JDT process alive)");
                    // Execute the actual LS command
                    LsSearchService.executeLsCmd(factory, rule);
                } else {
                    logger.error("  ❌ JDT process is dead, cannot execute LS command");
                }
            } else {
                logger.infof("  ⚠️  Rule has no java.referenced conditions");
            }

        } catch (Exception e) {
            logger.errorf("  ❌ Error executing rule: %s", e.getMessage());
            if (verbose) {
                logger.error("Stack trace:", e);
            }
        }
    }

    private void launchJdtProcess() throws Exception {
        // Use resolved paths from system properties
        String resolvedJdtLsPath = System.getProperty("JDT_LS_PATH");
        String resolvedJdtWorkspace = System.getProperty("JDT_WKS");

        logger.infof("📋 Launching JDT with resolved paths - LS: %s, Workspace: %s", resolvedJdtLsPath, resolvedJdtWorkspace);

        Path wksDir = Paths.get(resolvedJdtWorkspace);

        String os = System.getProperty("os.name").toLowerCase();
        logger.infof("📋 Detected OS: %s", os);

        Path configPath = os.contains("win") ? Paths.get(resolvedJdtLsPath, "config_win") :
            os.contains("mac") ? Paths.get(resolvedJdtLsPath, "config_mac_arm") :
                Paths.get(resolvedJdtLsPath, "config_linux");

        logger.infof("📋 Config path: %s", configPath);

        if (!configPath.toFile().exists()) {
            throw new Exception("JDT-LS config directory does not exist: " + configPath);
        }

        // Check plugins directory
        File pluginsDir = new File(resolvedJdtLsPath, "plugins");
        if (!pluginsDir.exists()) {
            throw new Exception("JDT-LS plugins directory does not exist: " + pluginsDir.getAbsolutePath());
        }

        File[] launcherFiles = pluginsDir.listFiles((dir, name) -> name.startsWith("org.eclipse.equinox.launcher_"));
        if (launcherFiles == null || launcherFiles.length == 0) {
            throw new Exception("No equinox launcher jar found in plugins directory: " + pluginsDir.getAbsolutePath());
        }

        String launcherJar = launcherFiles[0].getName();
        logger.infof("📋 Using launcher jar: %s", launcherJar);

        ProcessBuilder pb = new ProcessBuilder(
            "java",
            "-Declipse.application=org.eclipse.jdt.ls.core.id1",
            "-Dosgi.bundles.defaultStartLevel=4",
            "-Dosgi.checkConfiguration=true",
            "-Dosgi.sharedConfiguration.area.readOnly=true",
            "-Dosgi.configuration.cascaded=true",
            "-Declipse.product=org.eclipse.jdt.ls.core.product",
            "-Dlog.level=ALL",
            "-Djdt.ls.debug=true",
            "-noverify",
            "-Xmx1G",
            "--add-modules=ALL-SYSTEM",
            "--add-opens", "java.base/java.util=ALL-UNNAMED",
            "--add-opens", "java.base/java.lang=ALL-UNNAMED",
            "-jar", Paths.get(resolvedJdtLsPath, "plugins", launcherJar).toString(),
            "-configuration", configPath.toString(),
            "-data", wksDir.resolve(".jdt_workspace").toString()
        );
        pb.redirectErrorStream(true);

        String javaHome = Optional.ofNullable(System.getProperty("JAVA_HOME"))
            .orElse(System.getProperty("java.home"));

        Map<String, String> env = pb.environment();
        env.put("JAVA_HOME", javaHome);

        try {
            jdtProcess = pb.start();
            logger.infof("✅ JDT Language Server process started with PID: %s", jdtProcess.pid());

            // Add a small delay to ensure the process is fully started
            Thread.sleep(2000);

            if (!jdtProcess.isAlive()) {
                throw new Exception("JDT Language Server process died immediately after starting");
            }

        } catch (IOException exception) {
            throw new Exception("Failed to start JDT Language Server process: " + exception.getMessage(), exception);
        }
    }

    private void createLaunchLsClient() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        LSClient client = new LSClient();

        launcher = LSPLauncher.createClientLauncher(
            client,
            jdtProcess.getInputStream(),
            jdtProcess.getOutputStream(),
            executor,
            (writer) -> writer
        );

        launcher.startListening();
        logger.infof("✅ LSP client connected and listening");
    }

    private CompletableFuture<InitializeResult> initializeLanguageServer(String projectPath) throws Exception {
        InitializeParams p = new InitializeParams();
        p.setProcessId((int) ProcessHandle.current().pid());
        p.setRootUri(FileUtils.getApplicationDir(projectPath.toString()).toUri().toString());
        p.setCapabilities(new ClientCapabilities());

        String resolvedJdtLsPath = System.getProperty("JDT_LS_PATH");
        String bundlePath = String.format("[\"%s\"]",
            Paths.get(resolvedJdtLsPath, "java-analyzer-bundle", "java-analyzer-bundle.core", "target", "java-analyzer-bundle.core-1.0.0-SNAPSHOT.jar"));

        String json = String.format("""
            {
               "bundles": %s
            }""", bundlePath);

        Object initializationOptions = new Gson().fromJson(json, JsonObject.class);
        p.setInitializationOptions(initializationOptions);

        CompletableFuture<InitializeResult> future = remoteProxy.initialize(p);
        future.get(TIMEOUT, TimeUnit.MILLISECONDS);

        InitializedParams initialized = new InitializedParams();
        remoteProxy.initialized(initialized);

        logger.infof("✅ JDT Language Server initialized");
        return future;
    }
}
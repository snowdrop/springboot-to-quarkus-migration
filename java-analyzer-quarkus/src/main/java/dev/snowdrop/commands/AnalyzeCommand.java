package dev.snowdrop.commands;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.snowdrop.lsp.common.utils.FileUtils;
import dev.snowdrop.lsp.common.utils.LSClient;
import dev.snowdrop.lsp.common.utils.YamlRuleParser;
import dev.snowdrop.lsp.common.services.LsSearchService;
import dev.snowdrop.lsp.model.Rule;
import dev.snowdrop.lsp.JdtlsAndClient;
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

@CommandLine.Command(
    name = "analyze",
    description = "Analyze a project for migration"
)
public class AnalyzeCommand implements Runnable {

    private static final Logger logger = Logger.getLogger(AnalyzeCommand.class);

    @CommandLine.Parameters(
        index = "0",
        description = "Path to the Java project to analyze"
    )
    String appPath;

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

        // Start JDT-LS and analyze with rules
        try {
            startJdtAnalysis();
        } catch (Exception e) {
            logger.errorf("❌ Error during JDT analysis: %s", e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
        }

        logger.infof("\n📊 Analysis Summary:");
        logger.infof("- Migration: Ready for next step");
    }

    private void startJdtAnalysis() throws Exception {
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
        logger.infof("📋 Resolved JDT-LS path: %s", resolvedJdtLsPath);
        logger.infof("📋 Resolved workspace: %s", resolvedJdtWorkspace);
        logger.infof("📋 Resolved project path: %s", resolvedProjectPath);
        logger.infof("📋 Resolved rules path: %s", resolvedRulesPath);

        // Set system properties for JDT-LS
        System.setProperty("JDT_LS_PATH", resolvedJdtLsPath);
        System.setProperty("JDT_WKS", resolvedJdtWorkspace);
        System.setProperty("APP_PATH", resolvedProjectPath);
        System.setProperty("RULES_PATH", resolvedRulesPath);

        // Set LS_CMD in JdtlsAndClient from configuration
        JdtlsAndClient.LS_CMD = lsCommand;
        logger.infof("📋 LS_CMD set to: %s", JdtlsAndClient.LS_CMD);

        // Parse rules
        logger.infof("📋 Loading migration rules...");
        List<Rule> rules = loadRules();
        logger.infof("Found %s rules to execute", rules.size());

        if (rules.isEmpty()) {
            logger.infof("⚠️  No rules found, skipping JDT-LS analysis");
            return;
        }

        try {
            // Launch JDT-LS process
            logger.infof("🚀 Starting JDT Language Server process...");
            launchJdtProcess();

            // Create and launch the LS client
            logger.infof("🔌 Connecting to JDT Language Server...");
            createLaunchLsClient();

            remoteProxy = launcher.getRemoteProxy();

            // Initialize the language server
            logger.infof("⚙️  Initializing JDT Language Server...");
            CompletableFuture<InitializeResult> future = initializeLanguageServer(appPath);

            // Execute rules analysis
            logger.infof("🔍 Executing %s rules...", rules.size());
            for (Rule rule : rules) {
                logger.infof("\n🔍 Executing rule: %s", rule.ruleID());
                executeRule(future, rule);
            }

            // Wait a bit for async LS commands to complete
            logger.infof("⏳ Waiting for LS commands to complete...");
            Thread.sleep(5000); // Give time for async operations to complete

        } finally {
            // Clean up
            if (jdtProcess != null && jdtProcess.isAlive()) {
                logger.infof("🛑 Shutting down JDT Language Server...");
                jdtProcess.destroyForcibly();
            }
        }
    }

    private List<Rule> loadRules() {
        try {
            String resolvedRulesPath = System.getProperty("RULES_PATH");
            File rulesDir = new File(resolvedRulesPath);
            if (!rulesDir.exists()) {
                logger.errorf("⚠️  Rules directory not found: %s", resolvedRulesPath);
                return List.of();
            }

            return YamlRuleParser.parseRulesFromFolder(rulesDir.toPath());
        } catch (Exception e) {
            logger.errorf("❌ Error loading rules: %s", e.getMessage());
            return List.of();
        }
    }

    private void executeRule(CompletableFuture<InitializeResult> future, Rule rule) {
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
                    LsSearchService.executeLsCmd(future, remoteProxy, rule.withLsCmd(JdtlsAndClient.LS_CMD));
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

    private Path resolvePath(String pathString) {
        logger.debugf("📋 Resolving path: %s", pathString);

        if (pathString == null) {
            throw new IllegalArgumentException("Path string cannot be null");
        }

        Path path = Paths.get(pathString);
        if (path.isAbsolute()) {
            logger.debugf("📋 Path is already absolute: %s", path);
            return path;
        } else {
            // Resolve relative paths from user.dir
            Path resolved = Paths.get(System.getProperty("user.dir"), pathString);
            logger.debugf("📋 Resolved relative path '%s' to: %s", pathString, resolved);
            return resolved;
        }
    }
}
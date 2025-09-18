package dev.snowdrop.lsp;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.snowdrop.lsp.common.utils.LSClient;
import dev.snowdrop.lsp.model.Rule;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static dev.snowdrop.lsp.common.services.LsSearchService.executeLsCmd;
import static dev.snowdrop.lsp.common.utils.FileUtils.getApplicationDir;
import static dev.snowdrop.lsp.common.utils.YamlRuleParser.parseRulesFromFolder;

public class JdtlsAndClient {

    private static final Logger logger = LoggerFactory.getLogger(JdtlsAndClient.class);
    private static final long TIMEOUT = 30000;
    private static String JDT_LS_PATH;
    private static String JDT_WKS;
    private static String APP_PATH;
    private static Path RULES_PATH;
    public static String LS_CMD;

    private static Process process = null;
    private static LanguageServer remoteProxy;
    private static Launcher<LanguageServer> launcher;

    public static void main(String[] args) throws Exception {

        initProperties();
        launchLsProcess();
        createLaunchLsClient();

        remoteProxy = launcher.getRemoteProxy();

        // TODO : To review in order to pass some missing parameters from a JSON file
        InitializeParams p = new InitializeParams();
        p.setProcessId((int) ProcessHandle.current().pid());
        p.setRootUri(getApplicationDir(APP_PATH).toUri().toString());
        p.setCapabilities(new ClientCapabilities());

        String bundlePath = String.format("[\"%s\"]", Paths.get(JDT_LS_PATH, "java-analyzer-bundle", "java-analyzer-bundle.core", "target", "java-analyzer-bundle.core-1.0.0-SNAPSHOT.jar"));
        logger.info("bundle path is {}", bundlePath);

        String json = String.format("""
            {
               "bundles": %s
            }""", bundlePath);
        logger.info("initializationOptions {}", json);

        Object initializationOptions = new Gson().fromJson(json, JsonObject.class);
        p.setInitializationOptions(initializationOptions);

        CompletableFuture<InitializeResult> future = remoteProxy.initialize(p);
        future.get(TIMEOUT, TimeUnit.MILLISECONDS).toString();

        InitializedParams initialized = new InitializedParams();
        remoteProxy.initialized(initialized);

        // Parse the YAML rules to be tested against the project to be analyzed
        List<Rule> rules = parseRulesFromFolder(RULES_PATH);
        for (Rule rule : rules) {
            executeLsCmd(future, remoteProxy, rule.withLsCmd(LS_CMD));
        }
    }

    // Create and launch the LS client able to talk to the LS server
    private static void createLaunchLsClient() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        LSClient client = new LSClient();

        logger.info("Connecting to the JDT Language Server ...");
        launcher = LSPLauncher.createClientLauncher(
            client,
            process.getInputStream(),
            process.getOutputStream(),
            executor,
            (writer) -> writer // No-op, we don't want to wrap the writer
        );

        launcher.startListening();
    }

    private static void initProperties() {
        String jdtLsPath = Optional
            .ofNullable(System.getProperty("JDT_LS_PATH"))
            .orElseThrow(() -> new RuntimeException("JDT_LS_PATH system property is missing !"));
        JDT_LS_PATH = resolvePath(jdtLsPath).toString();

        String jdtWks = Optional
            .ofNullable(System.getProperty("JDT_WKS"))
            .orElseThrow(() -> new RuntimeException("JDT_WKS system property is missing !"));
        JDT_WKS = resolvePath(jdtWks).toString();

        String appPath = Optional
            .ofNullable(System.getProperty("APP_PATH"))
            .orElse("applications/spring-boot-todo-app");
        APP_PATH = resolvePath(appPath).toString();

        String rulesPath = Optional
            .ofNullable(System.getProperty("RULES_PATH"))
            .orElse("rules");
        RULES_PATH = resolvePath(rulesPath);

        LS_CMD = Optional
            .ofNullable(System.getProperty("LS_CMD"))
            .orElse("java.project.getAll");

        // Log resolved paths for debugging
        logger.info("Resolved JDT_LS_PATH: {}", JDT_LS_PATH);
        logger.info("Resolved JDT_WKS: {}", JDT_WKS);
        logger.info("Resolved APP_PATH: {}", APP_PATH);
        logger.info("Resolved RULES_PATH: {}", RULES_PATH);
        logger.info("LS_CMD: {}", LS_CMD);
    }

    private static Path resolvePath(String pathString) {
        Path path = Paths.get(pathString);
        if (path.isAbsolute()) {
            return path;
        } else {
            // Resolve relative paths from current working directory
            Path currentDir = Paths.get(System.getProperty("user.dir"));
            return currentDir.resolve(pathString).normalize().toAbsolutePath();
        }
    }

    private static void launchLsProcess() {
        Path wksDir = Paths.get(JDT_WKS);

        String os = System.getProperty("os.name").toLowerCase();
        Path configPath = os.contains("win") ? Paths.get(JDT_LS_PATH, "config_win") :
            os.contains("mac") ? Paths.get(JDT_LS_PATH, "config_mac_arm") :
                Paths.get(JDT_LS_PATH, "config_linux");

        String launcherJar = Objects
            .requireNonNull(
                new File(JDT_LS_PATH, "plugins")
                    .listFiles((dir, name) -> name.startsWith("org.eclipse.equinox.launcher_")))[0].getName();

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
            "-jar", Paths.get(JDT_LS_PATH, "plugins", launcherJar).toString(),
            "-configuration", configPath.toString(),
            "-data", wksDir.resolve(".jdt_workspace").toString()
        );
        pb.redirectErrorStream(true);

        String javaHome = Optional.ofNullable(System.getProperty("JAVA_HOME"))
            .orElse(System.getProperty("java.home"));

        Map<String, String> env = pb.environment();
        env.put("JAVA_HOME", javaHome);

        try {
            process = pb.start();
            logger.info("====== Language Server Process id: {} ====== ", process.info());
            logger.info("====== jdt ls started =======");
            logger.info("====== Workspace project directory: {} ======", wksDir);
        } catch (IOException exception) {
            logger.error("====== Failed to create process :{}", String.valueOf(exception));
            System.exit(1);
        }

    }
}
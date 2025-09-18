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

import static dev.snowdrop.lsp.common.services.LsSearchService.executeCmd;
import static dev.snowdrop.lsp.common.utils.FileUtils.getApplicationDir;
import static dev.snowdrop.lsp.common.utils.RuleUtils.getLocationCode;
import static dev.snowdrop.lsp.common.utils.YamlRuleParser.parseRulesFromFolder;

public class JdtlsAndClient {

    private static final Logger logger = LoggerFactory.getLogger(JdtlsAndClient.class);
    private static final long TIMEOUT = 5000;
    private static String JDT_LS_PATH;
    private static String JDT_WKS;
    private static String APP_PATH;
    private static Path RULES_PATH;

    private static Process process = null;

    public static void main(String[] args) throws Exception {
        JDT_LS_PATH = Optional
            .ofNullable(System.getProperty("JDT_LS_PATH"))
            .orElseThrow(() -> new RuntimeException("JDT_LS_PATH system property is missing !"));

        JDT_WKS = Optional
            .ofNullable(System.getProperty("JDT_WKS"))
            .orElseThrow(() -> new RuntimeException("JDT_WKS system property is missing !"));

        APP_PATH = Optional
            .ofNullable(System.getProperty("APP_PATH"))
            .orElse("applications/spring-boot-todo-app");

        RULES_PATH = Paths.get(Optional
            .ofNullable(System.getProperty("RULES_PATH"))
            .orElse(Paths.get(System.getProperty("user.dir"), "rules").toString()));

        Path wksDir = Paths.get(JDT_WKS);
        logger.info("Created workspace project directory: {}", wksDir);

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

        try {
            process = pb.start();
            logger.info("Process id: {}", process.info());
            logger.info("jdt ls started");
        } catch (IOException exception) {
            logger.error("Failed to create process :" + exception);
            System.exit(1);
        }

        Launcher<LanguageServer> launcher;
        ExecutorService executor;

        logger.info("Connecting to the JDT Language Server ...");

        executor = Executors.newSingleThreadExecutor();
        LSClient client = new LSClient();

        launcher = LSPLauncher.createClientLauncher(
            client,
            process.getInputStream(),
            process.getOutputStream(),
            executor,
            (writer) -> writer // No-op, we don't want to wrap the writer
        );

        launcher.startListening();

        LanguageServer remoteProxy = launcher.getRemoteProxy();

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

        // Send by example the command java.project.getAll to the jdt-ls as it supports it
        String cmd = Optional.ofNullable(System.getProperty("LS_CMD")).orElse("java.project.getAll");
        logger.info("CLIENT: Sending the command '{}' ...", cmd);

        List<Rule> rules = parseRulesFromFolder(RULES_PATH);
        for (Rule rule : rules) {
            Rule updatedRule = rule.withLsCmd(cmd);
            executeLsCmd(future, remoteProxy, updatedRule);
        }
    }

    private static void executeLsCmd(CompletableFuture<InitializeResult> future, LanguageServer remoteProxy, Rule rule) {
        var paramsMap = Map.of(
            "project", "java", // hard coded value to java within the analyzer java external-provider
            "location", getLocationCode(rule.when().javaReferenced().location()),
            "query", rule.when().javaReferenced().pattern(), // pattern from the rule
            "analysisMode", "source-only" // 2 modes are supported: source-only and full
        );

        List<Object> cmdArguments = List.of(paramsMap);

        future
            .thenRunAsync(() -> executeCmd(rule.lsCmd(), cmdArguments, remoteProxy))
            .exceptionally(throwable -> {
                logger.error("Error executing LS command for rule {}: {}", rule.ruleID(), throwable.getMessage(), throwable);
                return null;
            });
    }
}
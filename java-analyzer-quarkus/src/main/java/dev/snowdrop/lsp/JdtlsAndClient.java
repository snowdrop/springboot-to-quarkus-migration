package dev.snowdrop.lsp;

import com.google.gson.Gson;
import jakarta.enterprise.context.ApplicationScoped;
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
import org.jboss.logging.Logger;

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
import static dev.snowdrop.lsp.common.utils.FileUtils.resolvePath;
import static dev.snowdrop.lsp.common.utils.YamlRuleParser.parseRulesFromFolder;

@ApplicationScoped
public class JdtlsAndClient {
    private static final Logger logger = Logger.getLogger(JdtlsAndClient.class);
    private static final long TIMEOUT = 30000;
    public static String LS_CMD;

    private Process process = null;
    private LanguageServer remoteProxy;
    private Launcher<LanguageServer> launcher;
    private CompletableFuture<InitializeResult> future;

    private String jdtLsPath;
    private String jdtWks;
    private String appPath;
    private Path rulesPath;
    private String lsCmd;

    public static void main(String[] args) throws Exception {
        JdtlsAndClient client = new JdtlsAndClient();
        client.initProperties();
        client.launchLsProcess();
        client.createLaunchLsClient();
        client.initLanguageServer();
        client.analyze();
    }

    private void initProperties() {
        String jdtLsPathString = Optional
            .ofNullable(System.getProperty("JDT_LS_PATH"))
            .orElseThrow(() -> new RuntimeException("JDT_LS_PATH system property is missing !"));
        jdtLsPath = resolvePath(jdtLsPathString).toString();

        String jdtWksString = Optional
            .ofNullable(System.getProperty("JDT_WKS"))
            .orElseThrow(() -> new RuntimeException("JDT_WKS system property is missing !"));
        jdtWks = resolvePath(jdtWksString).toString();

        String appPathString = Optional
            .ofNullable(System.getProperty("APP_PATH"))
            .orElse("applications/spring-boot-todo-app");
        appPath = resolvePath(appPathString).toString();

        String rulesPathString = Optional
            .ofNullable(System.getProperty("RULES_PATH"))
            .orElse("rules");
        rulesPath = resolvePath(rulesPathString);

        lsCmd = Optional
            .ofNullable(System.getProperty("LS_CMD"))
            .orElse("java.project.getAll");

        // Log resolved paths for debugging
        logger.infof("Resolved JDT_LS_PATH: %s", jdtLsPath);
        logger.infof("Resolved JDT_WKS: %s", jdtWks);
        logger.infof("Resolved APP_PATH: %s", appPath);
        logger.infof("Resolved RULES_PATH: %s", rulesPath);
        logger.infof("LS_CMD: %s", lsCmd);
    }

    private void initLanguageServer() throws Exception {
        remoteProxy = launcher.getRemoteProxy();

        InitializeParams p = new InitializeParams();
        p.setProcessId((int) ProcessHandle.current().pid());
        p.setRootUri(getApplicationDir(appPath).toUri().toString());
        p.setCapabilities(new ClientCapabilities());

        String bundlePath = String.format("[\"%s\"]", Paths.get(jdtLsPath, "java-analyzer-bundle", "java-analyzer-bundle.core", "target", "java-analyzer-bundle.core-1.0.0-SNAPSHOT.jar"));
        logger.infof("bundle path is %s", bundlePath);

        String json = String.format("""
            {
               "bundles": %s
            }""", bundlePath);
        logger.infof("initializationOptions: %s", json);

        Object initializationOptions = new Gson().fromJson(json, JsonObject.class);
        p.setInitializationOptions(initializationOptions);

        future = remoteProxy.initialize(p);
        future.get(TIMEOUT, TimeUnit.MILLISECONDS).toString();

        InitializedParams initialized = new InitializedParams();
        remoteProxy.initialized(initialized);
    }

    private void launchLsProcess() {
        Path wksDir = Paths.get(jdtWks);

        String os = System.getProperty("os.name").toLowerCase();
        Path configPath = os.contains("win") ? Paths.get(jdtLsPath, "config_win") :
            os.contains("mac") ? Paths.get(jdtLsPath, "config_mac_arm") :
                Paths.get(jdtLsPath, "config_linux");

        String launcherJar = Objects
            .requireNonNull(
                new File(jdtLsPath, "plugins")
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
            "-jar", Paths.get(jdtLsPath, "plugins", launcherJar).toString(),
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
            logger.infof("====== Language Server Process id: %s ====== ", process.info());
            logger.infof("====== jdt ls started =======");
            logger.infof("====== Workspace project directory: %s ======", wksDir);
        } catch (IOException exception) {
            logger.errorf("====== Failed to create process :%s", String.valueOf(exception));
            System.exit(1);
        }

    }

    private void createLaunchLsClient() {
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
    }

    private void analyze() throws IOException {
        List<Rule> rules = parseRulesFromFolder(rulesPath);
        for (Rule rule : rules) {
            executeLsCmd(future, remoteProxy, rule.withLsCmd(lsCmd));
        }
    }
}
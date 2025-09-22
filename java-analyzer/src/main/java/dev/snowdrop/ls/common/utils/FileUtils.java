package dev.snowdrop.ls.common.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

public class FileUtils {
    private static final Logger logger = Logger.getLogger(FileUtils.class.getName());
    private static Path tempDir;

    public static Path getApplicationDir(String applicationPath) {
        return Paths.get(System.getProperty("user.dir"),applicationPath);
    }

    public static Path getExampleDir() {
        return Paths.get(System.getProperty("user.dir"),"example");
    }

    public static Path getTempDir() throws IOException {
        if (tempDir == null) {
            tempDir = Files.createTempDirectory("lsp");
        }
        logger.info("Project path: " + tempDir.toString());
        return tempDir;
    }
}

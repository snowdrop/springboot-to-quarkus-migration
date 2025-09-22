package dev.snowdrop.ls.model;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "analyzer")
public interface JdtLSConfiguration {
    String appPath();
    String rulesPath();
    String jdtLsPath();
    String jdtWorkspacePath();
    String jdtLsCommand();
}

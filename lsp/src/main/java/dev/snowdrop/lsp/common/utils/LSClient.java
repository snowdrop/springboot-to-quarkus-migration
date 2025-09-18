package dev.snowdrop.lsp.common.utils;


import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public class LSClient implements LanguageClient {
    private static final Logger logger = LoggerFactory.getLogger(LSClient.class);

    @Override
    public void telemetryEvent(Object object) {
        logger.info("telemetryEvent: {}", object);
    }

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
        logger.debug("publishDiagnostics: {}", diagnostics);
    }

    @Override
    public void showMessage(MessageParams messageParams) {
        logger.info("CLIENT: Message from server: [{}] {}", messageParams.getType(), messageParams.getMessage());
    }

    @Override
    public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
        logger.info("showMessageRequest: {}", requestParams);
        return new CompletableFuture<>();
    }

    @Override
    public void logMessage(MessageParams messageParams) {
        logger.info("CLIENT: Log from server: [{}] {}", messageParams.getType(), messageParams.getMessage());
    }
}

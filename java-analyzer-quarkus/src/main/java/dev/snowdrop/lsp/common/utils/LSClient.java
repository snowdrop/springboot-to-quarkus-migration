package dev.snowdrop.lsp.common.utils;


import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.services.LanguageClient;
import org.jboss.logging.Logger;

import java.util.concurrent.CompletableFuture;

public class LSClient implements LanguageClient {
    private static final Logger logger = Logger.getLogger(LSClient.class);

    @Override
    public void telemetryEvent(Object object) {
        logger.infof("telemetryEvent: {}", object);
    }

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
        logger.debugf("publishDiagnostics: {}", diagnostics);
    }

    @Override
    public void showMessage(MessageParams messageParams) {
        logger.infof("==== CLIENT: Message from server: [{}] {}", messageParams.getType(), messageParams.getMessage());
    }

    @Override
    public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
        logger.infof("showMessageRequest: {}", requestParams);
        return new CompletableFuture<>();
    }

    @Override
    public void logMessage(MessageParams messageParams) {
        logger.infof("==== CLIENT: Log from server: [{}] {}", messageParams.getType(), messageParams.getMessage());
    }
}

package dev.snowdrop.ls.model;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.SymbolKind;

/**
 * Represents LSP symbol information used for enhanced search context.
 */
public class LSPSymbolInfo {
    private final String name;
    private final String fileUri;
    private final SymbolKind kind;
    private final Location location;

    public LSPSymbolInfo(String name, String fileUri, SymbolKind kind, Location location) {
        this.name = name;
        this.fileUri = fileUri;
        this.kind = kind;
        this.location = location;
    }

    public String getName() {
        return name;
    }

    public String getFileUri() {
        return fileUri;
    }

    public SymbolKind getKind() {
        return kind;
    }

    public Location getLocation() {
        return location;
    }
}

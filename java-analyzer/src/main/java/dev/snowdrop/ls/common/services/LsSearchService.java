package dev.snowdrop.ls.common.services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import dev.snowdrop.ls.model.Rule;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static dev.snowdrop.ls.JdtlsAndClient.LS_CMD;
import static dev.snowdrop.ls.common.utils.RuleUtils.getLocationCode;
import static dev.snowdrop.ls.common.utils.RuleUtils.getLocationName;

public class LsSearchService {

    private static final Logger logger = LoggerFactory.getLogger(LsSearchService.class);

    public static void executeLsCmd(CompletableFuture<InitializeResult> future, LanguageServer remoteProxy, Rule rule) {

        // Log the LS Query command to be executed on the LS server
        logger.info("==== CLIENT: Sending the command '{}' ...", LS_CMD);

        // Handle three cases: single java.referenced, OR conditions, AND conditions
        if (rule.when().or() != null && !rule.when().or().isEmpty()) {
            logger.info("Rule When includes: {} between java.referenced", "OR");
            rule.when().or().forEach(condition ->
                executeCommandForCondition(future, remoteProxy, rule, condition.javaReferenced())
            );
        } else if (rule.when().and() != null && !rule.when().and().isEmpty()) {
            logger.info("Rule When includes: {} between java.referenced", "AND");
            rule.when().and().forEach(condition ->
                executeCommandForCondition(future, remoteProxy, rule, condition.javaReferenced())
            );
        } else if (rule.when().javaReferenced() != null) {
            logger.info("Rule When includes: single java.referenced");
            executeCommandForCondition(future, remoteProxy, rule, rule.when().javaReferenced());
        } else {
            logger.warn("Rule {} has no valid java.referenced conditions", rule.ruleID());
        }
    }

    private static void executeCommandForCondition(CompletableFuture<InitializeResult> future, LanguageServer remoteProxy, Rule rule, Rule.JavaReferenced javaReferenced) {
        var paramsMap = Map.of(
            "project", "java", // hard coded value to java within the analyzer java external-provider
            "location", getLocationCode(javaReferenced.location()),
            "query", javaReferenced.pattern(), // pattern from the rule
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


    public static void executeCmd(String customCmd, List<Object> arguments, LanguageServer LS) {
        List<Object> cmdArguments = (arguments != null && !arguments.isEmpty())
            ? arguments
            : Collections.EMPTY_LIST;

        ExecuteCommandParams commandParams = new ExecuteCommandParams(
            customCmd,
            cmdArguments
        );

        CompletableFuture<Object> commandResult = LS.getWorkspaceService()
            .executeCommand(commandParams)
            .exceptionally( t -> {
                    t.printStackTrace();
                    return null;
                });

        Object result = commandResult.join();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        List<SymbolInformation> symbolInformationList = new ArrayList<>();

        if (result != null) {
            logger.info("==== CLIENT: --- Search Results using as command: {}.", customCmd);
            // TODO This code should be reviewed to adapt it according to the objects returned as response
            logger.info("==== CLIENT: --- Result: {}", gson.toJson(result));

            // Following the Konveyor approach to create SymbolInformation objects
            try {
                if (result instanceof List) {
                    List<?> resultList = (List<?>) result;
                    for (Object item : resultList) {
                        SymbolInformation symbol = new SymbolInformation();

                        // Extract data from the result object and populate SymbolInformation
                        // This assumes the result contains objects with name, kind, and location data
                        if (item instanceof java.util.Map) {
                            java.util.Map<?, ?> itemMap = (java.util.Map<?, ?>) item;

                            // Set name if available
                            if (itemMap.containsKey("name")) {
                                symbol.setName(String.valueOf(itemMap.get("name")));
                            }

                            // Set kind if available (convert to SymbolKind)
                            if (itemMap.containsKey("kind")) {
                                Object kindValue = itemMap.get("kind");
                                if (kindValue instanceof Number) {
                                    symbol.setKind(SymbolKind.forValue(((Number) kindValue).intValue()));
                                }
                            }

                            // Set location if available
                            if (itemMap.containsKey("location")) {
                                // Parse location data - this would need to be adapted based on actual structure
                                Object locationData = itemMap.get("location");
                                Location location = gson.fromJson(gson.toJson(locationData), Location.class);
                                symbol.setLocation(location);
                            }

                            symbolInformationList.add(symbol);
                        }
                    }
                } else {
                    // Fallback to direct GSON conversion if result is not a List
                    Type SymbolInformationListType = new TypeToken<List<SymbolInformation>>() {}.getType();
                    symbolInformationList = gson.fromJson(gson.toJson(result), SymbolInformationListType);
                }
            } catch (Exception e) {
                logger.warn("==== CLIENT: Failed to create SymbolInformation objects: {}", e.getMessage());
                // Fallback to GSON conversion
                try {
                    Type SymbolInformationListType = new TypeToken<List<SymbolInformation>>() {}.getType();
                    symbolInformationList = gson.fromJson(gson.toJson(result), SymbolInformationListType);
                } catch (JsonSyntaxException | ClassCastException ex) {
                    logger.warn("==== CLIENT: Failed fallback GSON conversion: {}", ex.getMessage());
                }
            }

            if (symbolInformationList.isEmpty()) {
                logger.info("==== CLIENT: SymbolInformation List is empty.");
            } else {
                Map<String, Object> args = (Map<String, Object>) arguments.get(0);
                logger.info("==== CLIENT: Found {} usage(s) of symbol: {}, name: {}", symbolInformationList.size(), getLocationName(args.get("location").toString()),args.get("query"));
                for (SymbolInformation si : symbolInformationList) {
                    logger.info("==== CLIENT: Found {} at: {} (line {}, char: {} - {})",
                        si.getName(),
                        si.getLocation().getUri(),
                        si.getLocation().getRange().getStart().getLine() + 1,
                        si.getLocation().getRange().getStart().getCharacter(),
                        si.getLocation().getRange().getEnd().getCharacter()
                    );
                }
            }
            logger.info("==== CLIENT: ----------------------");
        } else {
            logger.warn("==== CLIENT: Received null result for command.");
        }
    }

    public static CompletableFuture<Optional<SymbolInformation>> searchWksSymbol(String annotationToFind, LanguageServer LS) {
        logger.info("==== CLIENT: Searching for the definition of '{}' within the java project...", annotationToFind);
        WorkspaceSymbolParams symbolParams = new WorkspaceSymbolParams(annotationToFind);

        return LS.getWorkspaceService().symbol(symbolParams)
            .thenApply(eitherResult -> {
                List<SymbolInformation> symbols = new ArrayList<>();
                if (eitherResult != null) {
                    if (eitherResult.isLeft()) symbols.addAll(eitherResult.getLeft());
                    else
                        symbols.addAll(eitherResult.getRight().stream().filter(ws -> ws.getLocation().isLeft()).map(ws -> new SymbolInformation(ws.getName(), ws.getKind(), ws.getLocation().getLeft())).collect(Collectors.toList()));
                }

                // An annotation in Java has the SymbolKind 'Interface' in LSP.
                return symbols.stream()
                    .filter(s -> s.getKind() == SymbolKind.Interface && s.getName().equals(annotationToFind))
                    .findFirst();
            })
            .exceptionally(t -> {
                t.printStackTrace();
                return null;
            });
    }

                /* OLD code
                List<LSPSymbolInfo> lspSymbols = new ArrayList<>();

                if (eitherResult.isLeft()) {
                    List<? extends SymbolInformation> symbols = eitherResult.getLeft();
                    for (SymbolInformation symbol : symbols) {
                        lspSymbols.add(new LSPSymbolInfo(
                            symbol.getName(),
                            symbol.getLocation().getUri(),
                            symbol.getKind(),
                            symbol.getLocation()
                        ));
                    }
                } else {
                    List<? extends WorkspaceSymbol> symbols = eitherResult.getRight();
                    for (WorkspaceSymbol symbol : symbols) {
                        if (symbol.getLocation().isLeft()) {
                            Location location = symbol.getLocation().getLeft();
                            lspSymbols.add(new LSPSymbolInfo(
                                symbol.getName(),
                                location.getUri(),
                                symbol.getKind(),
                                location
                            ));
                        }
                    }
                }

                logger.info("LSP workspace/symbol found {} symbols for '{}'", lspSymbols.size(), annotationToFind);
                return lspSymbols;

                })
                .thenAccept(lspSymbols -> {
                    logger.info("==== CLIENT: --- LSP workspace/symbol {} ---", lspSymbols.size());
                    for (LSPSymbolInfo l : lspSymbols) {
                        logger.info("==== CLIENT:  -> Found @{} on {} in file: {} (line {}, char {})",
                            annotationToFind,
                            "",
                            l.getFileUri(),
                            l.getLocation().getRange().getStart().getLine() + 1,
                            l.getLocation().getRange().getStart().getCharacter() + 1
                        );
                    }
                })
                .exceptionally((ex) -> {
                    logger.error("Failed to initialize language server: {}", ex.getMessage());
                    return null;
                });
                */

}
package dev.snowdrop.ls.services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import dev.snowdrop.ls.JdtLsFactory;
import dev.snowdrop.ls.model.Rule;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static dev.snowdrop.ls.utils.RuleUtils.getLocationCode;
import static dev.snowdrop.ls.utils.RuleUtils.getLocationName;
import static dev.snowdrop.ls.utils.YamlRuleParser.parseRulesFromFolder;

public class LsSearchService {

    private static final Logger logger = Logger.getLogger(LsSearchService.class);

    public static void executeLsCmd(JdtLsFactory factory, Rule rule) {

        // Log the LS Query command to be executed on the LS server
        logger.infof("==== CLIENT: Sending the command '%s' ...", factory.lsCmd);

        // Handle three cases: single java.referenced, OR conditions, AND conditions
        if (rule.when().or() != null && !rule.when().or().isEmpty()) {
            logger.infof("Rule When includes: %s between java.referenced", "OR");
            rule.when().or().forEach(condition ->
                executeCommandForCondition(factory, rule, condition.javaReferenced())
            );
        } else if (rule.when().and() != null && !rule.when().and().isEmpty()) {
            logger.infof("Rule When includes: %s between java.referenced", "AND");
            rule.when().and().forEach(condition ->
                executeCommandForCondition(factory, rule, condition.javaReferenced())
            );
        } else if (rule.when().javaReferenced() != null) {
            logger.infof("Rule When includes: single java.referenced");
            executeCommandForCondition(factory, rule, rule.when().javaReferenced());
        } else {
            logger.warnf("Rule %s has no valid java.referenced conditions", rule.ruleID());
        }
    }

    private static void executeCommandForCondition(JdtLsFactory factory, Rule rule, Rule.JavaReferenced javaReferenced) {
        var paramsMap = Map.of(
            "project", "java", // hard coded value to java within the analyzer java external-provider
            "location", getLocationCode(javaReferenced.location()),
            "query", javaReferenced.pattern(), // pattern from the rule
            "analysisMode", "source-only" // 2 modes are supported: source-only and full
        );

        List<Object> cmdArguments = List.of(paramsMap);

        factory.future
            .thenRunAsync(() -> executeCmd(factory.lsCmd, cmdArguments, factory.remoteProxy))
            .exceptionally(throwable -> {
                logger.errorf("Error executing LS command for rule %s: %s", rule.ruleID(), throwable.getMessage(), throwable);
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
            logger.infof("==== CLIENT: --- Search Results using as command: %s.", customCmd);
            // TODO This code should be reviewed to adapt it according to the objects returned as response
            logger.infof("==== CLIENT: --- Result: %s", gson.toJson(result));

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
                logger.warnf("==== CLIENT: Failed to create SymbolInformation objects: %s", e.getMessage());
                // Fallback to GSON conversion
                try {
                    Type SymbolInformationListType = new TypeToken<List<SymbolInformation>>() {}.getType();
                    symbolInformationList = gson.fromJson(gson.toJson(result), SymbolInformationListType);
                } catch (JsonSyntaxException | ClassCastException ex) {
                    logger.warnf("==== CLIENT: Failed fallback GSON conversion: %s", ex.getMessage());
                }
            }

            if (symbolInformationList.isEmpty()) {
                logger.infof("==== CLIENT: SymbolInformation List is empty.");
            } else {
                Map<String, Object> args = (Map<String, Object>) arguments.get(0);
                logger.infof("==== CLIENT: Found %s usage(s) of symbol: %s, name: %s", symbolInformationList.size(), getLocationName(args.get("location").toString()),args.get("query"));
                for (SymbolInformation si : symbolInformationList) {
                    logger.infof("==== CLIENT: Found %s at: %s (line %s, char: %s - %s)",
                        si.getName(),
                        si.getLocation().getUri(),
                        si.getLocation().getRange().getStart().getLine() + 1,
                        si.getLocation().getRange().getStart().getCharacter(),
                        si.getLocation().getRange().getEnd().getCharacter()
                    );
                }
            }
            logger.infof("==== CLIENT: ----------------------");
        } else {
            logger.warn("==== CLIENT: Received null result for command.");
        }
    }

    public static void analyzeCodeFromRule(JdtLsFactory factory) throws IOException {
        List<Rule> rules = parseRulesFromFolder(factory.rulesPath);
        for (Rule rule : rules) {
            executeLsCmd( factory, rule);
        }
    }

}
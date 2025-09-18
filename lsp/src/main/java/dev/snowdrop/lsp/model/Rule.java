package dev.snowdrop.lsp.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record Rule(
    String category,
    @JsonProperty("customVariables") List<String> customVariables,
    String description,
    int effort,
    List<String> labels,
    List<String> links,
    String message,
    String ruleID,
    String lsCmd,
    When when,
    List<String> actions
) {

    public Rule withLsCmd(String lsCmd) {
        return new Rule(category, customVariables, description, effort, labels, links, message, ruleID, lsCmd, when, actions);
    }

    public record When(
        @JsonProperty("java.referenced") JavaReferenced javaReferenced
    ) {}

    public record JavaReferenced(
        String location,
        String pattern
    ) {}
}
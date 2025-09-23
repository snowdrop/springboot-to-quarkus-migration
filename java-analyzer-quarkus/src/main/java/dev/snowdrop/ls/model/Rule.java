package dev.snowdrop.ls.model;

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
    @JsonProperty("actions") List<String> actions
) {

    public Rule withLsCmd(String lsCmd) {
        return new Rule(category, customVariables, description, effort, labels, links, message, ruleID, lsCmd, when, actions);
    }

    public record When(
        @JsonProperty("java.referenced")
        JavaReferenced javaReferenced,
        @JsonProperty("or")
        List<Condition> or,
        @JsonProperty("and")
        List<Condition> and
    ) {}

    public record Condition(
        @JsonProperty("java.dependency")
        JavaDependency javaDependency,

        @JsonProperty("java.referenced")
        JavaReferenced javaReferenced,
        String as,
        String from,
        String not,
        String ignore
    ) {}

    public record JavaReferenced(
        String location,
        String pattern,
        String filepaths,
        String annotated
    ) {}

    public record Annotated(
        String pattern,
        List<Element> elements
    ) {}
    
    public record Element(
        String name,
        String value
    ) {}

    public record JavaDependency(
        String lowerbound,
        String upperbound,
        String name,
        String nameregex
    ) {}
}
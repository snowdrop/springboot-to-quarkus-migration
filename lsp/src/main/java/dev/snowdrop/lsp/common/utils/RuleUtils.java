package dev.snowdrop.lsp.common.utils;

import dev.snowdrop.lsp.JdtlsAndClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RuleUtils {
    private static final Logger logger = LoggerFactory.getLogger(RuleUtils.class);
    public static String getLocationCode(String location) {
        if (location == null) {
            return "0";
        }

        return switch (location.toUpperCase()) {
            case "" -> "0";
            case "INHERITANCE" -> "1";
            case "METHOD_CALL" -> "2";
            case "CONSTRUCTOR_CALL" -> "3";
            case "ANNOTATION" -> "4";
            case "IMPLEMENTS_TYPE" -> "5";
            case "ENUM" -> "6";
            case "RETURN_TYPE" -> "7";
            case "IMPORT" -> "8";
            case "VARIABLE_DECLARATION" -> "9";
            case "TYPE" -> "10";
            case "PACKAGE" -> "11";
            case "FIELD" -> "12";
            case "METHOD" -> "13";
            case "CLASS" -> "14";
            default -> {
                logger.warn("Unknown location type '{}', defaulting to 0", location);
                yield "0";
            }
        };
    }
}

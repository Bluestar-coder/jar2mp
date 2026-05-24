package com.z0fsec.jar2mp.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SourcePostProcessor {

    private static final Pattern RAW_LIST_ENHANCED_FOR = Pattern.compile(
            "List\\s+(\\w+)\\s*=\\s*([^;]+);\\n(\\s*)for\\s*\\(([^:\\n]+?)\\s+(\\w+)\\s*:\\s*\\1\\)");
    private static final Pattern RAW_OPTIONAL_OR_ELSE_THROW = Pattern.compile(
            "Optional\\s+(\\w+)\\s*=\\s*([^;]+);\\n(\\s*)([\\w.$<>]+)\\s+(\\w+)\\s*=\\s*\\(([^)]+)\\)\\1\\.orElseThrow");

    public String process(String source) {
        if (source == null || source.isEmpty()) {
            return source;
        }

        String processed = source.replace("(Object)", "");
        processed = addListElementTypes(processed);
        processed = addOptionalElementTypes(processed);
        return processed;
    }

    private String addListElementTypes(String source) {
        Matcher matcher = RAW_LIST_ENHANCED_FOR.matcher(source);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String listName = matcher.group(1);
            String initializer = matcher.group(2);
            String indent = matcher.group(3);
            String elementType = matcher.group(4).trim();
            String elementName = matcher.group(5);
            String replacement = "List<" + elementType + "> " + listName + " = " + initializer + ";\n"
                    + indent + "for (" + elementType + " " + elementName + " : " + listName + ")";
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String addOptionalElementTypes(String source) {
        Matcher matcher = RAW_OPTIONAL_OR_ELSE_THROW.matcher(source);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String optionalName = matcher.group(1);
            String initializer = matcher.group(2);
            String indent = matcher.group(3);
            String variableType = matcher.group(4);
            String variableName = matcher.group(5);
            String castType = matcher.group(6);
            String optionalType = castType.trim();
            String replacement = "Optional<" + optionalType + "> " + optionalName + " = " + initializer + ";\n"
                    + indent + variableType + " " + variableName + " = " + optionalName + ".orElseThrow";
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}

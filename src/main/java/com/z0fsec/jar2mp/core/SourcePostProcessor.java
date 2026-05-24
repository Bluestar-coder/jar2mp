package com.z0fsec.jar2mp.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SourcePostProcessor {

    private static final Pattern DECOMPILER_HEADER = Pattern.compile(
            "\\A\\s*/\\*\\s*\\n(?: \\*.*\\n)*? \\*/\\s*", Pattern.DOTALL);
    private static final Pattern PACKAGE_DECLARATION = Pattern.compile("(?m)^package\\s+([\\w.]+);\\s*$");
    private static final Pattern IMPORT_DECLARATION = Pattern.compile("(?m)^import\\s+([\\w.]+);\\s*\\n");
    private static final Pattern RAW_LIST_ENHANCED_FOR = Pattern.compile(
            "List\\s+(\\w+)\\s*=\\s*([^;]+);\\n(\\s*)for\\s*\\(([^:\\n]+?)\\s+(\\w+)\\s*:\\s*\\1\\)");
    private static final Pattern RAW_OPTIONAL_OR_ELSE_THROW = Pattern.compile(
            "Optional\\s+(\\w+)\\s*=\\s*([^;]+);\\n(\\s*)([\\w.$<>]+)\\s+(\\w+)\\s*=\\s*\\(([^)]+)\\)\\1\\.orElseThrow");

    public String process(String source) {
        return process(source, null);
    }

    public String process(String source, String className) {
        if (source == null || source.isEmpty()) {
            return source;
        }

        String processed = stripDecompilerHeader(source);
        processed = removeRedundantImports(processed, className);
        processed = processed.replace("(Object)", "");
        processed = removeParameterArrayCasts(processed);
        processed = addListElementTypes(processed);
        processed = addOptionalElementTypes(processed);
        return processed;
    }

    private String stripDecompilerHeader(String source) {
        Matcher matcher = DECOMPILER_HEADER.matcher(source);
        if (!matcher.find()) {
            return source;
        }
        String header = matcher.group();
        if (!header.contains("Decompiled with") && !header.contains("Could not load the following classes")) {
            return source;
        }
        return source.substring(matcher.end());
    }

    private String removeRedundantImports(String source, String className) {
        String packageName = findPackageName(source, className);
        Matcher matcher = IMPORT_DECLARATION.matcher(source);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String imported = matcher.group(1);
            if (isImplicitImport(imported, packageName)) {
                matcher.appendReplacement(buffer, "");
            } else {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String findPackageName(String source, String className) {
        Matcher matcher = PACKAGE_DECLARATION.matcher(source);
        if (matcher.find()) {
            return matcher.group(1);
        }
        if (className == null) {
            return "";
        }
        int lastDot = className.lastIndexOf('.');
        return lastDot > 0 ? className.substring(0, lastDot) : "";
    }

    private boolean isImplicitImport(String imported, String packageName) {
        if (imported == null || imported.isEmpty()) {
            return false;
        }
        if (imported.startsWith("java.lang.") && imported.indexOf('.', "java.lang.".length()) < 0) {
            return true;
        }
        if (packageName == null || packageName.isEmpty() || !imported.startsWith(packageName + ".")) {
            return false;
        }
        String suffix = imported.substring(packageName.length() + 1);
        return !suffix.contains(".");
    }

    private String removeParameterArrayCasts(String source) {
        Matcher matcher = Pattern.compile("\\((String\\[\\])\\)(\\w+)").matcher(source);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String variableName = matcher.group(2);
            if (Pattern.compile("String\\[\\]\\s+" + Pattern.quote(variableName) + "\\b").matcher(source).find()) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(variableName));
            }
        }
        matcher.appendTail(buffer);
        return buffer.toString();
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

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
    private static final Pattern NUMERIC_ANONYMOUS_TYPE_DECLARATION = Pattern.compile(
            "(?m)^(\\s*)\\d+\\s+(\\w+)\\s*=");
    private static final Pattern NUMERIC_ANONYMOUS_CONSTRUCTOR = Pattern.compile("new\\s+\\d+\\([^;\\n]*\\)");
    private static final Pattern NUMERIC_ANONYMOUS_CAST = Pattern.compile("\\(\\d+\\)\\s*null");

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
        processed = replaceUnavailableAnonymousInnerClasses(processed);
        processed = replaceNumericAnonymousClassFragments(processed);
        processed = balanceNullArgumentStatements(processed);
        processed = castDoPrivilegedMethodReferences(processed);
        processed = removeUnreachableBreakAfterInfiniteLoop(processed);
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

    private String replaceUnavailableAnonymousInnerClasses(String source) {
        return source.replace("new /* Unavailable Anonymous Inner Class!! */", "null");
    }

    private String replaceNumericAnonymousClassFragments(String source) {
        String processed = NUMERIC_ANONYMOUS_CAST.matcher(source).replaceAll("null");
        processed = NUMERIC_ANONYMOUS_CONSTRUCTOR.matcher(processed).replaceAll("null");

        Matcher matcher = NUMERIC_ANONYMOUS_TYPE_DECLARATION.matcher(processed);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                    matcher.group(1) + "Object " + matcher.group(2) + " ="));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String balanceNullArgumentStatements(String source) {
        String[] lines = source.split("\\n", -1);
        StringBuilder builder = new StringBuilder(source.length());
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(balanceNullArgumentStatement(lines[i]));
        }
        return builder.toString();
    }

    private String balanceNullArgumentStatement(String line) {
        String trimmed = line.trim();
        if (!trimmed.endsWith("null;")) {
            return line;
        }

        int missingClosers = count(line, '(') - count(line, ')');
        if (missingClosers <= 0) {
            return line;
        }

        int semicolon = line.lastIndexOf(';');
        StringBuilder builder = new StringBuilder(line.length() + missingClosers);
        builder.append(line, 0, semicolon);
        for (int i = 0; i < missingClosers; i++) {
            builder.append(')');
        }
        builder.append(line.substring(semicolon));
        return builder.toString();
    }

    private String castDoPrivilegedMethodReferences(String source) {
        StringBuilder builder = new StringBuilder(source.length() + 32);
        int searchIndex = 0;
        while (true) {
            int callIndex = source.indexOf("AccessController.doPrivileged(", searchIndex);
            if (callIndex < 0) {
                builder.append(source, searchIndex, source.length());
                return builder.toString();
            }

            int lineStart = source.lastIndexOf('\n', callIndex) + 1;
            String prefix = source.substring(lineStart, callIndex);
            if (!prefix.trim().startsWith("return")) {
                builder.append(source, searchIndex, callIndex + 1);
                searchIndex = callIndex + 1;
                continue;
            }

            int openParen = callIndex + "AccessController.doPrivileged".length();
            int closeParen = findMatchingParen(source, openParen);
            if (closeParen < 0) {
                builder.append(source, searchIndex, callIndex + 1);
                searchIndex = callIndex + 1;
                continue;
            }

            int semicolon = closeParen + 1;
            while (semicolon < source.length() && Character.isWhitespace(source.charAt(semicolon))) {
                semicolon++;
            }
            if (semicolon >= source.length() || source.charAt(semicolon) != ';') {
                builder.append(source, searchIndex, callIndex + 1);
                searchIndex = callIndex + 1;
                continue;
            }

            String returnType = findEnclosingMethodReturnType(source, callIndex);
            if (returnType == null || "void".equals(returnType)) {
                builder.append(source, searchIndex, semicolon + 1);
            } else {
                builder.append(source, searchIndex, openParen + 1);
                builder.append("(PrivilegedAction<").append(returnType).append(">) ");
                builder.append(source, openParen + 1, semicolon + 1);
            }
            searchIndex = semicolon + 1;
        }
    }

    private String findEnclosingMethodReturnType(String source, int offset) {
        int openBrace = source.lastIndexOf('{', offset);
        if (openBrace < 0) {
            return null;
        }
        int signatureStart = Math.max(0, source.lastIndexOf('\n', openBrace) + 1);
        String signature = source.substring(signatureStart, openBrace).trim();
        signature = signature.replaceAll("\\s+throws\\s+.+$", "").trim();
        signature = signature.replaceAll("^(?:public|protected|private|static|final|synchronized|native|abstract|strictfp|default|\\s)+", "").trim();
        int openParen = signature.indexOf('(');
        if (openParen < 0) {
            return null;
        }
        int lastSpace = signature.lastIndexOf(' ', openParen);
        if (lastSpace < 0) {
            return null;
        }
        String returnType = signature.substring(0, lastSpace).trim();
        return returnType.isEmpty() ? null : returnType;
    }

    private int findMatchingParen(String source, int openParen) {
        if (openParen < 0 || openParen >= source.length() || source.charAt(openParen) != '(') {
            return -1;
        }
        int depth = 0;
        boolean inString = false;
        boolean inChar = false;
        boolean escaping = false;
        for (int i = openParen; i < source.length(); i++) {
            char current = source.charAt(i);
            if (inString) {
                if (escaping) {
                    escaping = false;
                } else if (current == '\\') {
                    escaping = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (inChar) {
                if (escaping) {
                    escaping = false;
                } else if (current == '\\') {
                    escaping = true;
                } else if (current == '\'') {
                    inChar = false;
                }
                continue;
            }
            if (current == '"') {
                inString = true;
                continue;
            }
            if (current == '\'') {
                inChar = true;
                continue;
            }
            if (current == '(') {
                depth++;
            } else if (current == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
                if (depth < 0) {
                    return -1;
                }
            }
        }
        return -1;
    }

    private String removeUnreachableBreakAfterInfiniteLoop(String source) {
        String[] lines = source.split("\\n", -1);
        StringBuilder builder = new StringBuilder(source.length());
        for (int i = 0; i < lines.length; i++) {
            if (isBreakAfterInfiniteLoop(lines, i)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(lines[i]);
        }
        return builder.toString();
    }

    private boolean isBreakAfterInfiniteLoop(String[] lines, int index) {
        if (index <= 0 || !"break;".equals(lines[index].trim())) {
            return false;
        }
        int depth = 0;
        boolean sawReturnOrThrow = false;
        for (int i = index - 1; i >= 0; i--) {
            String trimmed = lines[i].trim();
            depth += count(trimmed, '}');
            depth -= count(trimmed, '{');
            if (trimmed.startsWith("return ") || trimmed.startsWith("throw ")) {
                sawReturnOrThrow = true;
            }
            if (trimmed.startsWith("while (true)") && depth == 0) {
                return sawReturnOrThrow;
            }
            if (depth < 0) {
                return false;
            }
        }
        return false;
    }

    private int count(String value, char target) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == target) {
                count++;
            }
        }
        return count;
    }
}

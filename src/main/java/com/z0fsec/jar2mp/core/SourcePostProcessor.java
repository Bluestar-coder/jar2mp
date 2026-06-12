package com.z0fsec.jar2mp.core;

import java.util.Collections;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SourcePostProcessor {

    private static final Pattern DECOMPILER_HEADER = Pattern.compile(
            "\\A\\s*/\\*\\s*\\n(?:\\s*\\*[^\\n]*\\n)*\\s*\\*/\\s*");
    private static final Pattern PACKAGE_DECLARATION = Pattern.compile("(?m)^package\\s+([\\w.]+);\\s*$");
    private static final Pattern IMPORT_DECLARATION = Pattern.compile("(?m)^import\\s+([\\w.]+);\\s*\\n");
    private static final Pattern RAW_LIST_ENHANCED_FOR = Pattern.compile(
            "List\\s+(\\w+)\\s*=\\s*([^;]+);\\n(\\s*)for\\s*\\(([^:\\n]+?)\\s+(\\w+)\\s*:\\s*\\1\\)");
    private static final Pattern RAW_OPTIONAL_OR_ELSE_THROW = Pattern.compile(
            "Optional\\s+(\\w+)\\s*=\\s*([^;]+);\\n(\\s*)([\\w.$<>]+)\\s+(\\w+)\\s*=\\s*\\(([^)]+)\\)\\1\\.orElseThrow");
    private static final Pattern NUMERIC_GENERIC_COLLECTION_DECLARATION = Pattern.compile(
            "(?m)^(\\s*)([A-Za-z_$][\\w$]*)<\\d+>\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*new\\s+([A-Za-z_$][\\w$]*)<\\d+>\\(([^;\\n]*)\\);");
    private static final Pattern RAW_LIST_CAST_DECLARATION = Pattern.compile(
            "(?m)^(\\s*)List\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*\\(List\\)");
    private static final Pattern EXECUTION_EXCEPTION_CAUSE_DECLARATION = Pattern.compile(
            "(?m)^(\\s*)ExecutionException\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*([^;\\n]*\\?\\s*\\(Exception\\)[^;\\n]*);");
    private static final Pattern FOR_LOOP_COUNTER_DECLARATION = Pattern.compile(
            "(?m)^(\\s*)for\\s*\\(int\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*([^;\\n]+);");
    private static final Pattern UNDEFINED_GENERIC_TO_ARRAY_CAST = Pattern.compile(
            "\\.toArray\\s*\\(\\s*\\(T\\[\\]\\)\\s*new\\s+");
    private static final Pattern ENUM_SET_NONE_OF_WILDCARD_CLASS = Pattern.compile(
            "EnumSet\\.noneOf\\(\\s*(?!\\(Class\\))([^\\)]+?)\\s*\\)");
    private static final Pattern ENUM_VALUE_OF_WILDCARD_CLASS = Pattern.compile(
            "Enum\\.valueOf\\(\\s*(?!\\(Class\\))([A-Za-z_$][\\w$.]*(?:\\[[^\\]]+\\])?)\\s*,");
    private static final Pattern REGEX_TRANSFORMER_CONDITIONAL_DECLARATION = Pattern.compile(
            "(?m)^(\\s*)RegexTransformer\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*([^;\\n]*negatableOptionTransformer\\(\\)[^;\\n]*);");
    private static final Pattern QUALIFIED_INNER_INSTANCE_CREATION = Pattern.compile(
            "\\.new\\s+(?:[A-Za-z_$][\\w$]*\\.)+([A-Za-z_$][\\w$]*)\\(");
    private static final Pattern NUMERIC_ANONYMOUS_TYPE_DECLARATION = Pattern.compile(
            "(?m)^(\\s*)\\d+\\s+(\\w+)\\s*=");
    private static final Pattern CFR_VOID_TEMPORARY_LOCAL = Pattern.compile(
            "(?m)^(\\s*)void\\s+([A-Za-z_$][\\w$]*)\\s*;");
    private static final Pattern DECOMPILER_DIAGNOSTIC_BLOCK = Pattern.compile(
            "(?ms)^\\s*/\\*\\s*\\R(?:\\s*\\*\\s*(?:WARNING - [^\\n]*|Enabled force condition propagation|"
                    + "Lifted jumps to return sites|Unable to fully structure code|Loose catch block)\\s*\\R)+"
                    + "\\s*\\*/\\s*\\R?");
    private static final Pattern DECOMPILER_MONITOR_COMMENT = Pattern.compile(
            "(?m)^\\s*// \\*\\* Monitor(?:Enter|Exit)\\[[^\\n]*\\] \\(shouldn't be in output\\)\\s*\\R?");
    private static final Pattern WILDCARD_LAMBDA_PARAMETER = Pattern.compile(
            "^\\?\\s+(?:super|extends)\\s+.+\\s+([A-Za-z_$][\\w$]*)$");
    private static final Pattern NUMERIC_ANONYMOUS_CONSTRUCTOR = Pattern.compile("new\\s+\\d+\\([^;\\n]*\\)");
    private static final Pattern NUMERIC_ANONYMOUS_CAST = Pattern.compile("\\(\\d+\\)\\s*null");
    private static final Pattern SYNTHETIC_ENUM_SWITCH_SELECTOR = Pattern.compile(
            "switch\\s*\\(\\s*1\\.(\\$SwitchMap\\$[A-Za-z0-9_$]+)\\[([\\s\\S]+?)\\.ordinal\\(\\)\\]\\s*\\)");
    private static final Pattern SYNTHETIC_ENUM_SWITCH_CASE = Pattern.compile(
            "(\\bcase\\s+)(\\d+)(\\s*(?::|->))");

    public String process(String source) {
        return process(source, null);
    }

    public String process(String source, String className) {
        return process(source, className, Collections.emptyMap());
    }

    public String process(String source, String className, Map<String, Map<Integer, String>> switchCaseMaps) {
        if (source == null || source.isEmpty()) {
            return source;
        }

        String processed = stripDecompilerHeader(source);
        processed = removeDecompilerDiagnosticComments(processed);
        processed = removeRedundantImports(processed, className);
        processed = processed.replace("(Object)", "");
        processed = removeParameterArrayCasts(processed);
        processed = addListElementTypes(processed);
        processed = addOptionalElementTypes(processed);
        processed = addNumericGenericElementTypes(processed);
        processed = addObjectElementTypesToRawListCasts(processed);
        processed = widenExecutionExceptionCauseLocals(processed);
        processed = replaceAnsiTextSyntheticOuterReferences(processed);
        processed = hoistForLoopCountersReferencedByLaterLoops(processed);
        processed = removeUndefinedGenericToArrayCasts(processed);
        processed = castClassWildcardsForEnumFactories(processed);
        processed = restoreGenericDefaultExceptionHandlerConstruction(processed);
        processed = widenRegexTransformerConditionals(processed);
        processed = restorePositionalParamSpecListLocals(processed);
        processed = restoreWildcardClassReflectionLocals(processed);
        processed = castWildcardClassArrayElements(processed);
        processed = restoreGenericReturnLocalTypes(processed);
        processed = restoreDeferredAssignments(processed);
        processed = restoreSyntheticEnumSwitches(processed, className, switchCaseMaps);
        processed = removeWildcardBoundsFromLambdaParameters(processed);
        processed = replaceUnavailableAnonymousInnerClasses(processed);
        processed = replaceNumericAnonymousClassFragments(processed);
        processed = shortenQualifiedInnerInstanceCreations(processed);
        processed = replaceCfrVoidTemporaryLocals(processed);
        processed = balanceNullArgumentStatements(processed);
        processed = castDoPrivilegedMethodReferences(processed);
        processed = removeUnreachableBreakAfterInfiniteLoop(processed);
        return processed;
    }

    private String removeDecompilerDiagnosticComments(String source) {
        String processed = DECOMPILER_DIAGNOSTIC_BLOCK.matcher(source).replaceAll("");
        return DECOMPILER_MONITOR_COMMENT.matcher(processed).replaceAll("");
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

    private String addNumericGenericElementTypes(String source) {
        Matcher matcher = NUMERIC_GENERIC_COLLECTION_DECLARATION.matcher(source);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String variableName = matcher.group(3);
            String elementType = findEnhancedForElementType(source, matcher.end(), variableName);
            if (elementType == null) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group()));
                continue;
            }
            String replacement = matcher.group(1) + matcher.group(2) + "<" + elementType + "> "
                    + variableName + " = new " + matcher.group(4) + "<" + elementType + ">("
                    + matcher.group(5) + ");";
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String addObjectElementTypesToRawListCasts(String source) {
        Matcher matcher = RAW_LIST_CAST_DECLARATION.matcher(source);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            if (isConsumedByFirstElement(source, matcher.end(), matcher.group(2))) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                        matcher.group(1) + "List<Object> " + matcher.group(2) + " = (List<Object>)"));
            } else {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String widenExecutionExceptionCauseLocals(String source) {
        Matcher matcher = EXECUTION_EXCEPTION_CAUSE_DECLARATION.matcher(source);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                    matcher.group(1) + "Exception " + matcher.group(2) + " = " + matcher.group(3) + ";"));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String replaceAnsiTextSyntheticOuterReferences(String source) {
        return source.replace("Help.defaultColorScheme(this$0)", "Help.defaultColorScheme(Ansi.this)");
    }

    private String removeUndefinedGenericToArrayCasts(String source) {
        return UNDEFINED_GENERIC_TO_ARRAY_CAST.matcher(source).replaceAll(".toArray(new ");
    }

    private String castClassWildcardsForEnumFactories(String source) {
        String processed = ENUM_SET_NONE_OF_WILDCARD_CLASS.matcher(source)
                .replaceAll("EnumSet.noneOf((Class) $1)");
        processed = ENUM_VALUE_OF_WILDCARD_CLASS.matcher(processed)
                .replaceAll("Enum.valueOf((Class) $1,");
        if (processed.contains("EnumSet<?> enumSet")) {
            processed = processed.replace("return enumSet;", "return (Collection<Object>) enumSet;");
        }
        return processed;
    }

    private String restoreGenericDefaultExceptionHandlerConstruction(String source) {
        return source.replace("parseWithHandlers(handler, new DefaultExceptionHandler(), args)",
                "parseWithHandlers(handler, new DefaultExceptionHandler<R>(), args)");
    }

    private String restorePositionalParamSpecListLocals(String source) {
        if (!source.contains("positionals.subList")) {
            return source;
        }
        return source.replace("List<Object> missingList = Collections.emptyList();",
                "List<Model.PositionalParamSpec> missingList = Collections.emptyList();");
    }

    private String restoreWildcardClassReflectionLocals(String source) {
        if (!source.contains("getSuperclass()") && !source.contains("getAnnotation(Command.class)")) {
            return source;
        }
        return source
                .replace("Class<Object>", "Class<?>")
                .replace("Class cls;", "Class<?> cls;")
                .replace("Stack<Class> hierarchy = new Stack<Class>();",
                        "Stack<Class<?>> hierarchy = new Stack<Class<?>>();")
                .replace("cls = (Class)hierarchy.pop();", "cls = hierarchy.pop();")
                .replace("cls = (Class<Object>)hierarchy.pop();", "cls = hierarchy.pop();");
    }

    private String castWildcardClassArrayElements(String source) {
        if (!source.contains("Class<?>[] aux")) {
            return source;
        }
        return source.replace("result.add(aux[0]);", "result.add((Class) aux[0]);");
    }

    private String restoreGenericReturnLocalTypes(String source) {
        if (!source.contains("public V put(") || !source.contains("return removedValue;")) {
            return source;
        }
        return source.replace("Object removedValue =", "V removedValue =");
    }

    private String restoreDeferredAssignments(String source) {
        String[] lines = source.split("\\n", -1);
        StringBuilder builder = new StringBuilder(source.length());
        for (int i = 0; i < lines.length; i++) {
            String replacement = null;
            if (i + 1 < lines.length && "String result;".equals(lines[i].trim())) {
                replacement = restoreNestedStringAssignment(lines[i + 1]);
                if (replacement != null) {
                    i++;
                }
            } else if (i + 1 < lines.length && "int result;".equals(lines[i].trim())) {
                replacement = restoreNestedIntAssignment(lines[i + 1], "n", true);
                if (replacement != null) {
                    i++;
                }
            } else if (lines[i].contains("int n2 =") && lines[i].contains("(result = ")) {
                replacement = restoreNestedIntAssignment(lines[i], "n2", false);
            } else if (lines[i].contains("int n =") && lines[i].contains("(result = ")) {
                replacement = restoreNestedIntAssignment(lines[i], "n", false);
            } else if (i + 1 < lines.length && "String gram;".equals(lines[i].trim())) {
                replacement = splitMapPutAssignedKey(lines[i], lines[i + 1]);
                if (replacement != null) {
                    i++;
                }
            }
            appendLine(builder, replacement == null ? lines[i] : replacement);
        }
        return builder.toString();
    }

    private String restoreNestedStringAssignment(String line) {
        if (!line.contains("String string =") || !line.contains("(result = ")) {
            return null;
        }
        return line.replaceFirst("String\\s+string\\s*=", "String result =")
                .replace("(result = ", "(");
    }

    private String restoreNestedIntAssignment(String line, String temporaryName, boolean declareResult) {
        String marker = "int " + temporaryName + " =";
        int markerIndex = line.indexOf(marker);
        if (markerIndex < 0 || !line.contains("(result = ")) {
            return null;
        }
        String prefix = line.substring(0, markerIndex);
        String expression = line.substring(markerIndex + marker.length()).trim()
                .replace("(result = ", "(");
        return prefix + (declareResult ? "int result = " : "result = ") + expression;
    }

    private String splitMapPutAssignedKey(String declarationLine, String putLine) {
        String marker = "m.put(gram, 1 + (m.containsKey(gram = ";
        int markerIndex = putLine.indexOf(marker);
        if (markerIndex < 0 || !putLine.contains(") ? (Integer)m.get(gram) : 0));")) {
            return null;
        }
        String indent = declarationLine.substring(0, declarationLine.indexOf("String gram;"));
        String expression = putLine.substring(markerIndex + marker.length(),
                putLine.indexOf(") ? (Integer)m.get(gram) : 0));", markerIndex));
        String putReplacement = putLine.substring(0, markerIndex)
                + "m.put(gram, 1 + (m.containsKey(gram) ? (Integer)m.get(gram) : 0));";
        return indent + "String gram = " + expression + ";\n" + putReplacement;
    }

    private void appendLine(StringBuilder builder, String line) {
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(line);
    }

    private String widenRegexTransformerConditionals(String source) {
        Matcher matcher = REGEX_TRANSFORMER_CONDITIONAL_DECLARATION.matcher(source);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                    matcher.group(1) + "INegatableOptionTransformer " + matcher.group(2) + " = " + matcher.group(3) + ";"));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String hoistForLoopCountersReferencedByLaterLoops(String source) {
        Matcher matcher = FOR_LOOP_COUNTER_DECLARATION.matcher(source);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String variableName = matcher.group(2);
            if (isForLoopCounterReferencedByLaterLoop(source, matcher.end(), variableName)) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                        matcher.group(1) + "int " + variableName + ";\n"
                                + matcher.group(1) + "for (" + variableName + " = " + matcher.group(3) + ";"));
            } else {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private boolean isForLoopCounterReferencedByLaterLoop(String source, int startOffset, String variableName) {
        Pattern pattern = Pattern.compile("\\bfor\\s*\\([^;\\n]*=\\s*" + Pattern.quote(variableName) + "\\s*;");
        return pattern.matcher(source).find(startOffset);
    }

    private boolean isConsumedByFirstElement(String source, int startOffset, String variableName) {
        Pattern pattern = Pattern.compile("\\bfirstElement\\s*\\(\\s*" + Pattern.quote(variableName) + "\\s*\\)");
        return pattern.matcher(source).find(startOffset);
    }

    private String findEnhancedForElementType(String source, int startOffset, String iterableName) {
        Pattern pattern = Pattern.compile(
                "for\\s*\\(\\s*([^:\\n]+?)\\s+[A-Za-z_$][\\w$]*\\s*:\\s*"
                        + Pattern.quote(iterableName) + "\\s*\\)");
        Matcher matcher = pattern.matcher(source);
        if (!matcher.find(startOffset)) {
            return null;
        }
        String elementType = matcher.group(1).trim();
        return elementType.isEmpty() ? null : elementType;
    }

    private String replaceUnavailableAnonymousInnerClasses(String source) {
        return source
                .replace("new /* Unavailable Anonymous Inner Class!! */", "null")
                .replace("new /* invalid duplicate definition of identical inner class */", "null");
    }

    private String removeWildcardBoundsFromLambdaParameters(String source) {
        String[] lines = source.split("\\n", -1);
        StringBuilder builder = new StringBuilder(source.length());
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(removeWildcardBoundsFromLambdaParameterLine(lines[i]));
        }
        return builder.toString();
    }

    private String removeWildcardBoundsFromLambdaParameterLine(String line) {
        int arrow = line.indexOf("->");
        if (arrow < 0 || line.indexOf('?') < 0) {
            return line;
        }
        int openParen = line.lastIndexOf('(', arrow);
        int closeParen = line.lastIndexOf(')', arrow);
        if (openParen < 0 || closeParen < openParen) {
            return line;
        }

        String[] params = line.substring(openParen + 1, closeParen).split(",");
        StringBuilder replacement = new StringBuilder();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) {
                replacement.append(", ");
            }
            replacement.append(removeWildcardBoundFromLambdaParameter(params[i].trim()));
        }
        return line.substring(0, openParen + 1) + replacement + line.substring(closeParen);
    }

    private String removeWildcardBoundFromLambdaParameter(String parameter) {
        Matcher matcher = WILDCARD_LAMBDA_PARAMETER.matcher(parameter);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return parameter;
    }

    private String restoreSyntheticEnumSwitches(String source, String className,
                                                Map<String, Map<Integer, String>> switchCaseMaps) {
        if (!source.contains(".$SwitchMap$") || switchCaseMaps == null || switchCaseMaps.isEmpty()) {
            return source;
        }

        Matcher matcher = SYNTHETIC_ENUM_SWITCH_SELECTOR.matcher(source);
        StringBuilder builder = new StringBuilder(source.length());
        int lastAppend = 0;
        int searchFrom = 0;
        while (matcher.find(searchFrom)) {
            String fieldName = matcher.group(1);
            Map<Integer, String> caseMap = findSwitchCaseMap(switchCaseMaps, className, fieldName);
            if (caseMap == null || caseMap.isEmpty()) {
                searchFrom = matcher.end();
                continue;
            }

            int openBrace = findNextChar(source, '{', matcher.end());
            int closeBrace = findMatchingBrace(source, openBrace);
            if (openBrace < 0 || closeBrace < 0) {
                searchFrom = matcher.end();
                continue;
            }

            builder.append(source, lastAppend, matcher.start());
            builder.append("switch (").append(matcher.group(2).trim()).append(")");
            builder.append(replaceSyntheticEnumSwitchCases(source.substring(openBrace, closeBrace + 1), caseMap));
            lastAppend = closeBrace + 1;
            searchFrom = closeBrace + 1;
        }
        if (lastAppend == 0) {
            return source;
        }
        builder.append(source, lastAppend, source.length());
        return builder.toString();
    }

    private Map<Integer, String> findSwitchCaseMap(Map<String, Map<Integer, String>> switchCaseMaps,
                                                   String className,
                                                   String fieldName) {
        if (className != null && !className.isEmpty()) {
            Map<Integer, String> exact = switchCaseMaps.get(className + "#" + fieldName);
            if (exact != null) {
                return exact;
            }
            Map<Integer, String> sourceStyle = switchCaseMaps.get(className.replace('$', '.') + "#" + fieldName);
            if (sourceStyle != null) {
                return sourceStyle;
            }
        }
        return switchCaseMaps.get(fieldName);
    }

    private String replaceSyntheticEnumSwitchCases(String switchBlock, Map<Integer, String> caseMap) {
        Matcher matcher = SYNTHETIC_ENUM_SWITCH_CASE.matcher(switchBlock);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String enumConstant = caseMap.get(Integer.parseInt(matcher.group(2)));
            if (enumConstant == null) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group()));
            } else {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                        matcher.group(1) + enumConstant + matcher.group(3)));
            }
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private int findNextChar(String source, char target, int startOffset) {
        for (int i = Math.max(0, startOffset); i < source.length(); i++) {
            if (source.charAt(i) == target) {
                return i;
            }
        }
        return -1;
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

    private String shortenQualifiedInnerInstanceCreations(String source) {
        Matcher matcher = QUALIFIED_INNER_INSTANCE_CREATION.matcher(source);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(".new " + matcher.group(1) + "("));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String replaceCfrVoidTemporaryLocals(String source) {
        Matcher matcher = CFR_VOID_TEMPORARY_LOCAL.matcher(source);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                    matcher.group(1) + "Object " + matcher.group(2) + " = null;"));
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
        int depth = 0;
        for (int i = offset; i >= 0; i--) {
            char current = source.charAt(i);
            if (current == '}') {
                depth++;
            } else if (current == '{') {
                if (depth > 0) {
                    depth--;
                    continue;
                }
                String returnType = extractMethodReturnType(source, i);
                if (returnType != null) {
                    return returnType;
                }
            }
        }
        return null;
    }

    private String extractMethodReturnType(String source, int openBrace) {
        int signatureStart = Math.max(0, source.lastIndexOf('\n', openBrace) + 1);
        String signature = source.substring(signatureStart, openBrace).trim();
        signature = signature.replaceAll("\\s+throws\\s+.+$", "").trim();
        signature = signature.replaceAll("^(?:public|protected|private|static|final|synchronized|native|abstract|strictfp|default|\\s)+", "").trim();
        signature = signature.replaceAll("^<[^>]+>\\s+", "").trim();
        int openParen = signature.indexOf('(');
        if (openParen < 0) {
            return null;
        }
        String beforeParen = signature.substring(0, openParen).trim();
        if (isControlBlockKeyword(beforeParen)) {
            return null;
        }
        int lastSpace = signature.lastIndexOf(' ', openParen);
        if (lastSpace < 0) {
            return null;
        }
        String returnType = signature.substring(0, lastSpace).trim();
        return returnType.isEmpty() ? null : returnType;
    }

    private boolean isControlBlockKeyword(String beforeParen) {
        return "if".equals(beforeParen)
                || "for".equals(beforeParen)
                || "while".equals(beforeParen)
                || "switch".equals(beforeParen)
                || "catch".equals(beforeParen)
                || "try".equals(beforeParen)
                || "synchronized".equals(beforeParen);
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

    private int findMatchingBrace(String source, int openBrace) {
        if (openBrace < 0 || openBrace >= source.length() || source.charAt(openBrace) != '{') {
            return -1;
        }
        int depth = 0;
        boolean inString = false;
        boolean inChar = false;
        boolean escaping = false;
        for (int i = openBrace; i < source.length(); i++) {
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
            if (current == '{') {
                depth++;
            } else if (current == '}') {
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

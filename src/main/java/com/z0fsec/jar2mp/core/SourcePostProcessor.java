package com.z0fsec.jar2mp.core;

import java.util.Collections;
import java.util.LinkedHashMap;
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
    private static final Pattern RAW_COLLECTION_ENHANCED_FOR = Pattern.compile(
            "(List|Set|HashSet|ArrayList)\\s+(\\w+)\\s*=\\s*([^;]+);\\n"
                    + "(\\s*)for\\s*\\(([^:\\n]+?)\\s+(\\w+)\\s*:\\s*\\2\\)");
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
    private static final Pattern METHOD_REFERENCE_TYPE = Pattern.compile(
            "\\b([A-Z][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][\\w$]*)*)::[A-Za-z_$][\\w$]*");
    private static final Pattern LAMBDA_QUERY_WRAPPER_DECLARATION = Pattern.compile(
            "\\bLambdaQueryWrapper\\s+([A-Za-z_$][\\w$]*)\\s*=");
    private static final Pattern LAMBDA_QUERY_WRAPPER_ASSIGNMENT = Pattern.compile(
            "\\b([A-Za-z_$][\\w$]*)\\s*=\\s*(?:\\(LambdaQueryWrapper(?:<[^>]+>)?\\)|new\\s+LambdaQueryWrapper(?:<[^>]+>)?\\()");
    private static final Pattern PAGE_INFO_ROW_LIST_METHOD_REFERENCE = Pattern.compile(
            "\\b([A-Za-z_$][\\w$]*)\\.getRowList\\(\\)\\.stream\\(\\)\\.map\\(\\s*"
                    + "([A-Z][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][\\w$]*)*)::");
    private static final Pattern GENERIC_METHOD_DECLARATION = Pattern.compile(
            "(?m)^\\s*(?:(?:public|protected|private|static|final|synchronized)\\s+)*"
                    + "([A-Za-z_$][\\w$.]*\\s*<[^\\n;{}]+>)\\s+([A-Za-z_$][\\w$]*)\\s*\\(");
    private static final Pattern PAGE_DATA_METHOD_DECLARATION = Pattern.compile(
            "\\b(?:Result\\s*<\\s*)?PageData\\s*<\\s*([A-Za-z_$][\\w$.]*)\\s*>\\s*>?\\s+"
                    + "[A-Za-z_$][\\w$]*\\s*\\(");
    private static final Pattern LIST_METHOD_DECLARATION = Pattern.compile(
            "\\bList\\s*<\\s*([A-Za-z_$][\\w$.]*)\\s*>\\s+[A-Za-z_$][\\w$]*\\s*\\(");
    private static final Pattern WRAPPED_LIST_METHOD_DECLARATION = Pattern.compile(
            "\\b[A-Za-z_$][\\w$.]*\\s*<\\s*List\\s*<\\s*([A-Za-z_$][\\w$.]*)\\s*>\\s*>\\s+"
                    + "[A-Za-z_$][\\w$]*\\s*\\(");
    private static final Pattern TYPED_LIST_DECLARATION = Pattern.compile(
            "\\bList\\s*<\\s*([A-Za-z_$][\\w$.]*)\\s*>\\s+([A-Za-z_$][\\w$]*)\\b");
    private static final Pattern RAW_LIST_DECLARATION_WITH_INITIALIZER = Pattern.compile(
            "(?m)^(\\s*)List\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*([^;\\n]+;)");
    private static final Pattern MAP_METHOD_DECLARATION = Pattern.compile(
            "\\bMap\\s*<\\s*([^,>]+)\\s*,\\s*([^>]+?)\\s*>\\s+[A-Za-z_$][\\w$]*\\s*\\([^)]*\\)\\s*\\{");
    private static final Pattern CHAR_SEQUENCE_SPLIT_ARRAY = Pattern.compile(
            "(?m)^(\\s*)CharSequence\\[\\]\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*([^;\\n]*\\.split\\([^;\\n]*\\);)");
    private static final Pattern RAW_ARRAY_LIST_DECLARATION = Pattern.compile(
            "(?m)^(\\s*)ArrayList\\s+([A-Za-z_$][\\w$]*)\\s*;");

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
        processed = unwrapSingleElementRedisSetOperationArrays(processed);
        processed = restoreStringSplitArrayTypes(processed);
        processed = removeGuavaListFactoryCasts(processed);
        processed = restoreStringListLocalsFromFactoryAssignments(processed);
        processed = addImmutableMapBuilderTypeArguments(processed);
        processed = removeRepeatedValidationAnnotations(processed);
        processed = addListElementTypes(processed);
        processed = addOptionalElementTypes(processed);
        processed = addNumericGenericElementTypes(processed);
        processed = addObjectElementTypesToRawListCasts(processed);
        processed = restoreLambdaQueryWrapperEntityTypes(processed);
        processed = restoreLambdaUpdateWrapperEntityTypes(processed);
        processed = restoreLambdaQueryChainWrapperEntityTypes(processed);
        processed = restoreLambdaUpdateChainWrapperEntityTypes(processed);
        processed = restoreLambdaQueryChainListElementTypes(processed);
        processed = restorePageInfoRowListElementTypes(processed);
        processed = restoreLocalTypesFromGenericMethodReturns(processed);
        processed = restorePageInfoLocalsFromPageDataReturnTypes(processed);
        processed = restoreListLocalsFromListReturnTypes(processed);
        processed = restoreNestedListPartitionTypes(processed);
        processed = restoreGenericListMethodTypeVariableLocals(processed);
        processed = restorePageDataLocalsFromResultReturnTypes(processed);
        processed = restoreRawListElementTypesFromStreamMethodReferences(processed);
        processed = alignStreamMethodReferenceOwnersWithListElementTypes(processed);
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

    private String removeRepeatedValidationAnnotations(String source) {
        String processed = source;
        String previous;
        do {
            previous = processed;
            processed = processed.replaceAll(
                    "@Valid\\s+(@NotNull(?:\\([^)]*\\))?)\\s+@Valid\\s+\\1",
                    "@Valid $1");
            processed = processed.replaceAll("(@[A-Za-z_$][\\w$.]*(?:\\([^)]*\\))?)\\s+\\1", "$1");
        } while (!processed.equals(previous));
        return processed;
    }

    private String restoreLambdaQueryWrapperEntityTypes(String source) {
        String[] lines = source.split("\\n", -1);
        Map<String, String> currentWrapperTypes = new LinkedHashMap<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.contains("LambdaQueryWrapper") && line.contains("::")) {
                String entityType = firstMethodReferenceType(line);
                if (entityType != null) {
                    String typedWrapper = "LambdaQueryWrapper<" + entityType + ">";
                    Map<String, String> lineWrapperTypes = lambdaQueryWrapperVariables(line, entityType);
                    line = line.replace("new LambdaQueryWrapper()", "new " + typedWrapper + "()");
                    line = line.replace("(LambdaQueryWrapper)", "(" + typedWrapper + ")");
                    line = replaceLambdaQueryWrapperDeclarations(line, typedWrapper);
                    for (Map.Entry<String, String> entry : lineWrapperTypes.entrySet()) {
                        currentWrapperTypes.put(entry.getKey(), entry.getValue());
                        typeNearestPreviousLambdaQueryWrapperDeclaration(lines, i, entry.getKey(), entry.getValue());
                        typeNearestPreviousListDeclarationFromAssignment(lines, i, line, entry.getKey(), entry.getValue());
                    }
                }
            }
            line = typeListAssignmentLine(line, currentWrapperTypes);
            lines[i] = line;
        }

        return String.join("\n", lines);
    }

    private String restoreLambdaUpdateWrapperEntityTypes(String source) {
        String[] lines = source.split("\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (!line.contains("LambdaUpdateWrapper") || !line.contains("::")) {
                continue;
            }
            String entityType = firstMethodReferenceType(line, "LambdaUpdateWrapper");
            if (entityType == null) {
                continue;
            }
            String typedWrapper = "LambdaUpdateWrapper<" + entityType + ">";
            line = line.replace("new LambdaUpdateWrapper()", "new " + typedWrapper + "()");
            line = line.replace("(LambdaUpdateWrapper)", "(" + typedWrapper + ")");
            lines[i] = line;
        }
        return String.join("\n", lines);
    }

    private String firstMethodReferenceType(String line) {
        int wrapperStart = line.indexOf("new LambdaQueryWrapper");
        if (wrapperStart < 0) {
            wrapperStart = line.indexOf("(LambdaQueryWrapper");
        }
        String search = wrapperStart >= 0 ? line.substring(wrapperStart) : line;
        Matcher matcher = METHOD_REFERENCE_TYPE.matcher(search);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1);
    }

    private String firstMethodReferenceType(String line, String wrapperName) {
        int wrapperStart = line.indexOf("new " + wrapperName);
        if (wrapperStart < 0) {
            wrapperStart = line.indexOf("(" + wrapperName);
        }
        String search = wrapperStart >= 0 ? line.substring(wrapperStart) : line;
        Matcher matcher = METHOD_REFERENCE_TYPE.matcher(search);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1);
    }

    private Map<String, String> lambdaQueryWrapperVariables(String line, String entityType) {
        Map<String, String> wrapperTypes = new LinkedHashMap<>();
        Matcher declaration = LAMBDA_QUERY_WRAPPER_DECLARATION.matcher(line);
        while (declaration.find()) {
            wrapperTypes.put(declaration.group(1), entityType);
        }
        Matcher assignment = LAMBDA_QUERY_WRAPPER_ASSIGNMENT.matcher(line);
        while (assignment.find()) {
            wrapperTypes.put(assignment.group(1), entityType);
        }
        return wrapperTypes;
    }

    private String replaceLambdaQueryWrapperDeclarations(String line, String typedWrapper) {
        Matcher matcher = LAMBDA_QUERY_WRAPPER_DECLARATION.matcher(line);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(typedWrapper + " " + matcher.group(1) + " ="));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private void typeNearestPreviousLambdaQueryWrapperDeclaration(String[] lines, int currentIndex,
                                                                  String wrapperName, String entityType) {
        Pattern declaration = Pattern.compile(
                "\\bLambdaQueryWrapper\\s+" + Pattern.quote(wrapperName) + "\\s*([;=])");
        for (int i = currentIndex - 1; i >= 0; i--) {
            Matcher matcher = declaration.matcher(lines[i]);
            if (matcher.find()) {
                lines[i] = matcher.replaceFirst(Matcher.quoteReplacement(
                        "LambdaQueryWrapper<" + entityType + "> " + wrapperName) + "$1");
                return;
            }
            if (lines[i].contains("{") || lines[i].contains("}")) {
                return;
            }
        }
    }

    private void typeNearestPreviousListDeclarationFromAssignment(String[] lines, int currentIndex, String line,
                                                                  String wrapperName, String entityType) {
        Pattern assignment = Pattern.compile("\\b([A-Za-z_$][\\w$]*)\\s*=\\s*[^;\\n]*\\blist\\s*"
                + "\\(\\s*\\(Wrapper\\)\\s*\\(*\\s*"
                + Pattern.quote(wrapperName)
                + "\\b");
        Matcher assignmentMatcher = assignment.matcher(line);
        while (assignmentMatcher.find()) {
            String listName = assignmentMatcher.group(1);
            Pattern declaration = Pattern.compile("\\bList\\s+" + Pattern.quote(listName) + "\\s*;");
            for (int i = currentIndex - 1; i >= 0; i--) {
                Matcher declarationMatcher = declaration.matcher(lines[i]);
                if (declarationMatcher.find()) {
                    lines[i] = declarationMatcher.replaceFirst(
                            Matcher.quoteReplacement("List<" + entityType + "> " + listName + ";"));
                    break;
                }
                if (lines[i].contains("{") || lines[i].contains("}")) {
                    break;
                }
            }
        }
    }

    private String typeListAssignmentLine(String line, Map<String, String> currentWrapperTypes) {
        String processed = line;
        for (Map.Entry<String, String> entry : currentWrapperTypes.entrySet()) {
            Pattern listAssignment = Pattern.compile("^(\\s*)List\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*"
                    + "([^;\\n]*\\blist\\s*\\(\\s*\\(Wrapper\\)\\s*"
                    + Pattern.quote(entry.getKey())
                    + "\\s*\\)[^;\\n]*;)");
            processed = typeListAssignment(processed, listAssignment, entry.getValue());
        }
        Pattern inlineAssignment = Pattern.compile("^(\\s*)List\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*"
                + "([^;\\n]*new\\s+LambdaQueryWrapper<([A-Za-z_$][\\w$.]*)>\\(\\)[^;\\n]*;)");
        Matcher matcher = inlineAssignment.matcher(processed);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                    matcher.group(1) + "List<" + matcher.group(4) + "> " + matcher.group(2) + " = " + matcher.group(3)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String typeListAssignment(String line, Pattern listAssignment, String entityType) {
        Matcher matcher = listAssignment.matcher(line);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                    matcher.group(1) + "List<" + entityType + "> " + matcher.group(2) + " = " + matcher.group(3)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String restorePageInfoRowListElementTypes(String source) {
        Matcher matcher = PAGE_INFO_ROW_LIST_METHOD_REFERENCE.matcher(source);
        Map<String, String> pageInfoTypes = new LinkedHashMap<>();
        while (matcher.find()) {
            pageInfoTypes.putIfAbsent(matcher.group(1), matcher.group(2));
        }
        String processed = source;
        for (Map.Entry<String, String> entry : pageInfoTypes.entrySet()) {
            Pattern declaration = Pattern.compile(
                    "\\bPageInfo\\s+" + Pattern.quote(entry.getKey()) + "\\s*([;=])");
            processed = declaration.matcher(processed)
                    .replaceAll(Matcher.quoteReplacement("PageInfo<" + entry.getValue() + "> "
                            + entry.getKey()) + "$1");
        }
        return processed;
    }

    private String restoreLambdaQueryChainWrapperEntityTypes(String source) {
        String[] lines = source.split("\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (!line.contains("LambdaQueryChainWrapper") || !line.contains("::")) {
                continue;
            }
            String entityType = firstLambdaQueryChainWrapperMethodReferenceType(line);
            if (entityType == null) {
                continue;
            }
            line = line.replace("(LambdaQueryChainWrapper)", "(LambdaQueryChainWrapper<" + entityType + ">)");
            lines[i] = line;
        }
        return String.join("\n", lines);
    }

    private String restoreLambdaUpdateChainWrapperEntityTypes(String source) {
        String[] lines = source.split("\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (!line.contains("LambdaUpdateChainWrapper") || !line.contains("::")) {
                continue;
            }
            String entityType = firstLambdaUpdateChainWrapperMethodReferenceType(line);
            if (entityType == null) {
                continue;
            }
            line = line.replace("(LambdaUpdateChainWrapper)", "(LambdaUpdateChainWrapper<" + entityType + ">)");
            lines[i] = line;
        }
        return String.join("\n", lines);
    }

    private String firstLambdaQueryChainWrapperMethodReferenceType(String line) {
        int wrapperStart = line.indexOf("(LambdaQueryChainWrapper");
        if (wrapperStart < 0) {
            wrapperStart = line.indexOf(".lambdaQuery()");
        }
        String search = wrapperStart >= 0 ? line.substring(wrapperStart) : line;
        Matcher matcher = METHOD_REFERENCE_TYPE.matcher(search);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1);
    }

    private String firstLambdaUpdateChainWrapperMethodReferenceType(String line) {
        int wrapperStart = line.indexOf("(LambdaUpdateChainWrapper");
        if (wrapperStart < 0) {
            wrapperStart = line.indexOf(".lambdaUpdate()");
        }
        String search = wrapperStart >= 0 ? line.substring(wrapperStart) : line;
        Matcher matcher = METHOD_REFERENCE_TYPE.matcher(search);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1);
    }

    private String restoreLambdaQueryChainListElementTypes(String source) {
        Matcher matcher = Pattern.compile("(?m)^(\\s*)List\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*"
                + "([^;\\n]*LambdaQueryChainWrapper<([A-Za-z_$][\\w$.]*)>[^;\\n]*\\.list\\(\\)[^;\\n]*;)")
                .matcher(source);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                    matcher.group(1) + "List<" + matcher.group(4) + "> "
                            + matcher.group(2) + " = " + matcher.group(3)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String restorePageDataLocalsFromResultReturnTypes(String source) {
        String[] lines = source.split("\\n", -1);
        String currentElementType = null;
        Map<String, String> pageDataLocals = new LinkedHashMap<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Matcher methodMatcher = PAGE_DATA_METHOD_DECLARATION.matcher(line);
            if (methodMatcher.find()) {
                currentElementType = methodMatcher.group(1);
                pageDataLocals.clear();
            } else if (looksLikeMethodDeclaration(line)) {
                currentElementType = null;
                pageDataLocals.clear();
            }
            if (currentElementType == null) {
                continue;
            }

            Matcher pageDataMatcher = Pattern.compile("\\bPageData\\s+([A-Za-z_$][\\w$]*)\\s*([;=])")
                    .matcher(line);
            StringBuffer pageDataBuffer = new StringBuffer();
            while (pageDataMatcher.find()) {
                String localName = pageDataMatcher.group(1);
                if (isPageDataListMappedByMapstruct(source, localName)) {
                    pageDataMatcher.appendReplacement(pageDataBuffer, Matcher.quoteReplacement(pageDataMatcher.group()));
                    continue;
                }
                pageDataLocals.put(localName, currentElementType);
                pageDataMatcher.appendReplacement(pageDataBuffer, Matcher.quoteReplacement(
                        "PageData<" + currentElementType + "> " + localName) + pageDataMatcher.group(2));
            }
            pageDataMatcher.appendTail(pageDataBuffer);
            line = pageDataBuffer.toString();

            for (Map.Entry<String, String> entry : pageDataLocals.entrySet()) {
                Pattern listFromPageData = Pattern.compile("^(\\s*)List\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*"
                        + Pattern.quote(entry.getKey())
                        + "\\.getList\\(\\);");
                Matcher listMatcher = listFromPageData.matcher(line);
                if (listMatcher.find()) {
                    line = listMatcher.replaceFirst(Matcher.quoteReplacement(
                            listMatcher.group(1) + "List<" + entry.getValue() + "> "
                                    + listMatcher.group(2) + " = " + entry.getKey() + ".getList();"));
                }
            }
            lines[i] = line;
        }
        return String.join("\n", lines);
    }

    private boolean looksLikeMethodDeclaration(String line) {
        return line.matches("\\s*(?:public|protected|private)\\s+[^=;{}]+\\([^;{}]*\\)\\s*\\{?\\s*");
    }

    private boolean isPageDataListMappedByMapstruct(String source, String pageDataName) {
        Pattern mappedList = Pattern.compile("\\b[A-Za-z_$][\\w$]*Mapstruct\\.INSTANCE\\.toList\\s*"
                + "\\(\\s*" + Pattern.quote(pageDataName) + "\\.getList\\(\\)\\s*\\)");
        return mappedList.matcher(source).find();
    }

    private String restorePageInfoLocalsFromPageDataReturnTypes(String source) {
        String[] lines = source.split("\\n", -1);
        String currentElementType = null;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Matcher methodMatcher = PAGE_DATA_METHOD_DECLARATION.matcher(line);
            if (methodMatcher.find()) {
                currentElementType = methodMatcher.group(1);
            } else if (looksLikeMethodDeclaration(line)) {
                currentElementType = null;
            }
            if (currentElementType != null) {
                Matcher pageInfoMatcher = Pattern.compile("\\bPageInfo\\s+([A-Za-z_$][\\w$]*)\\s*([;=])")
                        .matcher(line);
                StringBuffer buffer = new StringBuffer();
                while (pageInfoMatcher.find()) {
                    String localName = pageInfoMatcher.group(1);
                    if (isPageInfoRowsTransformed(source, localName)) {
                        pageInfoMatcher.appendReplacement(buffer, Matcher.quoteReplacement(pageInfoMatcher.group()));
                        continue;
                    }
                    pageInfoMatcher.appendReplacement(buffer, Matcher.quoteReplacement(
                            "PageInfo<" + currentElementType + "> " + localName) + pageInfoMatcher.group(2));
                }
                pageInfoMatcher.appendTail(buffer);
                line = buffer.toString();
            }
            lines[i] = line;
        }
        return String.join("\n", lines);
    }

    private boolean isPageInfoRowsTransformed(String source, String pageInfoName) {
        Pattern mappedRows = Pattern.compile("\\b[A-Za-z_$][\\w$]*Mapstruct\\.INSTANCE\\.[A-Za-z_$][\\w$]*\\s*"
                + "\\(\\s*" + Pattern.quote(pageInfoName) + "\\.getRowList\\(\\)\\s*\\)");
        Pattern streamedRows = Pattern.compile("\\b" + Pattern.quote(pageInfoName)
                + "\\.getRowList\\(\\)\\.stream\\(\\)\\.map\\s*\\(");
        return mappedRows.matcher(source).find() || streamedRows.matcher(source).find();
    }

    private String restoreListLocalsFromListReturnTypes(String source) {
        String[] lines = source.split("\\n", -1);
        String currentElementType = null;
        int methodStart = -1;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Matcher methodMatcher = LIST_METHOD_DECLARATION.matcher(line);
            if (methodMatcher.find()) {
                currentElementType = methodMatcher.group(1);
                methodStart = i;
            } else {
                Matcher wrappedMethodMatcher = WRAPPED_LIST_METHOD_DECLARATION.matcher(line);
                if (wrappedMethodMatcher.find()) {
                    currentElementType = wrappedMethodMatcher.group(1);
                    methodStart = i;
                } else if (looksLikeMethodDeclaration(line)) {
                    currentElementType = null;
                    methodStart = -1;
                }
            }
            if (currentElementType == null || methodStart < 0) {
                continue;
            }
            Matcher listMatcher = Pattern.compile("\\bList\\s+([A-Za-z_$][\\w$]*)\\s*=")
                    .matcher(line);
            if (listMatcher.find() && isListReturnedBeforeNextMethod(lines, i + 1, listMatcher.group(1))) {
                lines[i] = listMatcher.replaceFirst(Matcher.quoteReplacement(
                        "List<" + currentElementType + "> " + listMatcher.group(1) + " ="));
            }
        }
        return String.join("\n", lines);
    }

    private boolean isListReturnedBeforeNextMethod(String[] lines, int start, String localName) {
        return isReturnedBeforeNextMethod(lines, start, localName)
                || isPassedToReturnCallBeforeNextMethod(lines, start, localName);
    }

    private boolean isReturnedBeforeNextMethod(String[] lines, int start, String localName) {
        Pattern returned = Pattern.compile("\\breturn\\s+" + Pattern.quote(localName) + "\\s*;");
        for (int i = start; i < lines.length; i++) {
            if (looksLikeMethodDeclaration(lines[i])) {
                return false;
            }
            if (returned.matcher(lines[i]).find()) {
                return true;
            }
        }
        return false;
    }

    private boolean isPassedToReturnCallBeforeNextMethod(String[] lines, int start, String localName) {
        Pattern returned = Pattern.compile("\\breturn\\s+[^;\\n]+\\(\\s*" + Pattern.quote(localName) + "\\s*\\)\\s*;");
        for (int i = start; i < lines.length; i++) {
            if (looksLikeMethodDeclaration(lines[i])) {
                return false;
            }
            if (returned.matcher(lines[i]).find()) {
                return true;
            }
        }
        return false;
    }

    private String restoreNestedListPartitionTypes(String source) {
        String[] lines = source.split("\\n", -1);
        Map<String, String> listElementTypes = new LinkedHashMap<>();
        Map<String, String> partitionElementTypes = new LinkedHashMap<>();
        Pattern partitionAssignment = Pattern.compile("^(\\s*)List(?:\\s*<\\s*List\\s*>)?\\s+"
                + "([A-Za-z_$][\\w$]*)\\s*=\\s*"
                + "((?:com\\.google\\.common\\.collect\\.)?Lists\\.partition\\(\\s*)"
                + "(?:\\(List\\)\\s*)?([A-Za-z_$][\\w$]*)\\s*,\\s*(?:\\(int\\)\\s*)?([^;\\n]+)\\);");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (looksLikeMethodDeclaration(line)) {
                listElementTypes.clear();
                partitionElementTypes.clear();
            }

            Matcher typedListMatcher = TYPED_LIST_DECLARATION.matcher(line);
            while (typedListMatcher.find()) {
                listElementTypes.put(typedListMatcher.group(2), typedListMatcher.group(1));
            }

            Matcher partitionMatcher = partitionAssignment.matcher(line);
            if (partitionMatcher.find()) {
                String partitionName = partitionMatcher.group(2);
                String sourceListName = partitionMatcher.group(4);
                String elementType = listElementTypes.get(sourceListName);
                if (elementType != null) {
                    partitionElementTypes.put(partitionName, elementType);
                    line = partitionMatcher.replaceFirst(Matcher.quoteReplacement(
                            partitionMatcher.group(1) + "List<List<" + elementType + ">> " + partitionName + " = "
                                    + partitionMatcher.group(3) + sourceListName + ", "
                                    + partitionMatcher.group(5).trim() + ");"));
                }
            }

            for (Map.Entry<String, String> entry : partitionElementTypes.entrySet()) {
                line = typeNestedListEnhancedFor(line, entry.getKey(), entry.getValue());
            }
            lines[i] = line;
        }
        return String.join("\n", lines);
    }

    private String typeNestedListEnhancedFor(String line, String partitionName, String elementType) {
        Pattern enhancedFor = Pattern.compile("\\bfor\\s*\\(\\s*List\\s+([A-Za-z_$][\\w$]*)\\s*:\\s*"
                + Pattern.quote(partitionName) + "\\s*\\)");
        Matcher matcher = enhancedFor.matcher(line);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                    "for (List<" + elementType + "> " + matcher.group(1) + " : " + partitionName + ")"));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String restoreGenericListMethodTypeVariableLocals(String source) {
        String[] lines = source.split("\\n", -1);
        String currentTypeVariable = null;
        Map<String, String> typedCollections = new LinkedHashMap<>();
        Pattern genericListMethod = Pattern.compile(
                "<[^>]*\\b([A-Z][A-Za-z0-9_$]*)\\b[^>]*>\\s+List\\s*<\\s*\\1\\s*>\\s+[A-Za-z_$][\\w$]*\\s*\\(");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Matcher methodMatcher = genericListMethod.matcher(line);
            if (methodMatcher.find()) {
                currentTypeVariable = methodMatcher.group(1);
                typedCollections.clear();
            } else if (looksLikeMethodDeclaration(line)) {
                currentTypeVariable = null;
                typedCollections.clear();
            }
            if (currentTypeVariable == null) {
                continue;
            }

            Matcher collectionMatcher = Pattern.compile("\\bArrayList<Object>\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*"
                    + "new\\s+ArrayList<Object>\\(").matcher(line);
            StringBuffer collectionBuffer = new StringBuffer();
            while (collectionMatcher.find()) {
                String collectionName = collectionMatcher.group(1);
                typedCollections.put(collectionName, currentTypeVariable);
                collectionMatcher.appendReplacement(collectionBuffer, Matcher.quoteReplacement(
                        "ArrayList<" + currentTypeVariable + "> " + collectionName
                                + " = new ArrayList<" + currentTypeVariable + ">("));
            }
            collectionMatcher.appendTail(collectionBuffer);
            line = collectionBuffer.toString();

            for (Map.Entry<String, String> entry : typedCollections.entrySet()) {
                Matcher objectLocal = Pattern.compile("\\bObject\\s+([A-Za-z_$][\\w$]*)\\s*=").matcher(line);
                if (objectLocal.find() && isAddedBeforeNextMethod(lines, i + 1, entry.getKey(), objectLocal.group(1))) {
                    line = objectLocal.replaceFirst(Matcher.quoteReplacement(entry.getValue() + " "
                            + objectLocal.group(1) + " ="));
                    break;
                }
            }
            lines[i] = line;
        }
        return String.join("\n", lines);
    }

    private boolean isAddedBeforeNextMethod(String[] lines, int start, String collectionName, String localName) {
        Pattern addCall = Pattern.compile("\\b" + Pattern.quote(collectionName)
                + "\\.add\\s*\\(\\s*" + Pattern.quote(localName) + "\\s*\\)");
        for (int i = start; i < lines.length; i++) {
            if (looksLikeMethodDeclaration(lines[i])) {
                return false;
            }
            if (addCall.matcher(lines[i]).find()) {
                return true;
            }
        }
        return false;
    }

    private String restoreLocalTypesFromGenericMethodReturns(String source) {
        Matcher declarationMatcher = GENERIC_METHOD_DECLARATION.matcher(source);
        Map<String, String> returnTypes = new LinkedHashMap<>();
        while (declarationMatcher.find()) {
            returnTypes.putIfAbsent(declarationMatcher.group(2), normalizeGenericType(declarationMatcher.group(1)));
        }

        String processed = source;
        for (Map.Entry<String, String> entry : returnTypes.entrySet()) {
            String genericType = entry.getValue();
            String rawSimpleName = rawSimpleTypeName(genericType);
            Pattern assignment = Pattern.compile("(?m)^(\\s*)"
                    + Pattern.quote(rawSimpleName)
                    + "\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*((?:this\\.|[A-Za-z_$][\\w$]*\\.)?"
                    + Pattern.quote(entry.getKey())
                    + "\\s*\\([^;\\n]*;)");
            Matcher matcher = assignment.matcher(processed);
            StringBuffer buffer = new StringBuffer();
            while (matcher.find()) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                        matcher.group(1) + genericType + " " + matcher.group(2) + " = " + matcher.group(3)));
            }
            matcher.appendTail(buffer);
            processed = buffer.toString();
        }
        return processed;
    }

    private String restoreRawListElementTypesFromStreamMethodReferences(String source) {
        Matcher matcher = RAW_LIST_DECLARATION_WITH_INITIALIZER.matcher(source);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String listName = matcher.group(2);
            String initializer = matcher.group(3);
            if (initializer.contains(".stream().map(")) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group()));
                continue;
            }
            String elementType = findStreamMethodReferenceOwner(source, matcher.end(), listName);
            if (elementType == null) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group()));
                continue;
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                    matcher.group(1) + "List<" + elementType + "> " + listName + " = " + initializer));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String findStreamMethodReferenceOwner(String source, int start, String listName) {
        Pattern streamMethodReference = Pattern.compile("\\b" + Pattern.quote(listName)
                + "\\.stream\\(\\)\\.map\\(\\s*([A-Z][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][\\w$]*)*)::"
                + "([A-Za-z_$][\\w$]*)");
        String methodRemainder = source.substring(start, nextMethodStart(source, start));
        Matcher matcher = streamMethodReference.matcher(methodRemainder);
        if (matcher.find() && isBeanAccessorMethodReference(matcher.group(2))) {
            return matcher.group(1);
        }

        Pattern collectorsToMapMethodReference = Pattern.compile("\\b" + Pattern.quote(listName)
                + "\\.stream\\(\\)\\.collect\\(\\s*Collectors\\.toMap\\(\\s*"
                + "([A-Z][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][\\w$]*)*)::([A-Za-z_$][\\w$]*)");
        Matcher toMapMatcher = collectorsToMapMethodReference.matcher(methodRemainder);
        if (toMapMatcher.find() && isBeanAccessorMethodReference(toMapMatcher.group(2))) {
            return toMapMatcher.group(1);
        }
        return null;
    }

    private int nextMethodStart(String source, int start) {
        Matcher matcher = Pattern.compile("(?m)^\\s*(?:public|protected|private)\\s+[^=;{}]+\\([^;{}]*\\)\\s*\\{?")
                .matcher(source);
        if (matcher.find(start)) {
            return matcher.start();
        }
        return source.length();
    }

    private String alignStreamMethodReferenceOwnersWithListElementTypes(String source) {
        String[] lines = source.split("\\n", -1);
        Map<String, String> listElementTypes = new LinkedHashMap<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (looksLikeMethodDeclaration(line)) {
                listElementTypes.clear();
            }
            Matcher declarationMatcher = TYPED_LIST_DECLARATION.matcher(line);
            while (declarationMatcher.find()) {
                listElementTypes.put(declarationMatcher.group(2), declarationMatcher.group(1));
            }
            for (Map.Entry<String, String> entry : listElementTypes.entrySet()) {
                line = alignStreamMethodReferenceOwnerInLine(line, entry.getKey(), entry.getValue());
            }
            lines[i] = line;
        }
        return String.join("\n", lines);
    }

    private String alignStreamMethodReferenceOwnerInLine(String line, String listName, String elementType) {
            Pattern streamMap = Pattern.compile("\\b"
                    + Pattern.quote(listName)
                    + "\\.stream\\(\\)\\.map\\(\\s*([A-Za-z_$][\\w$.]*)::([A-Za-z_$][\\w$]*)");
            Matcher matcher = streamMap.matcher(line);
            StringBuffer buffer = new StringBuffer();
            while (matcher.find()) {
                if (!isBeanAccessorMethodReference(matcher.group(2))) {
                    matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group()));
                    continue;
                }
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                        listName + ".stream().map(" + elementType + "::" + matcher.group(2)));
            }
            matcher.appendTail(buffer);
            return buffer.toString();
    }

    private boolean isBeanAccessorMethodReference(String methodName) {
        return methodName.matches("(?:get|is)[A-Z].*");
    }

    private String restoreStringSplitArrayTypes(String source) {
        Matcher matcher = CHAR_SEQUENCE_SPLIT_ARRAY.matcher(source);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                    matcher.group(1) + "String[] " + matcher.group(2) + " = " + matcher.group(3)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String addImmutableMapBuilderTypeArguments(String source) {
        String[] lines = source.split("\\n", -1);
        String currentMapArgs = null;
        int braceDepth = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Matcher methodMatcher = MAP_METHOD_DECLARATION.matcher(line);
            if (methodMatcher.find()) {
                currentMapArgs = methodMatcher.group(1).trim() + ", " + methodMatcher.group(2).trim();
                braceDepth = 0;
            }
            if (currentMapArgs != null && line.contains("ImmutableMap.builder()")) {
                line = line.replace("ImmutableMap.builder()", "ImmutableMap.<" + currentMapArgs + ">builder()");
            }
            if (currentMapArgs != null && line.contains("ImmutableMap.<" + currentMapArgs + ">builder()")) {
                line = castImmutableMapLambdaToCurrentMapType(line, currentMapArgs);
            }
            lines[i] = line;
            if (currentMapArgs != null) {
                braceDepth += countChar(line, '{') - countChar(line, '}');
                if (braceDepth <= 0) {
                    currentMapArgs = null;
                }
            }
        }
        return String.join("\n", lines);
    }

    private String castImmutableMapLambdaToCurrentMapType(String line, String mapArgs) {
        Pattern lambdaBuilder = Pattern.compile("\\.map\\(\\s*([A-Za-z_$][\\w$]*)\\s*->\\s*"
                + "ImmutableMap\\.<" + Pattern.quote(mapArgs) + ">builder\\(\\)");
        Matcher matcher = lambdaBuilder.matcher(line);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                    ".map(" + matcher.group(1) + " -> (Map<" + mapArgs + ">)ImmutableMap.<"
                            + mapArgs + ">builder()"));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private int countChar(String value, char target) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == target) {
                count++;
            }
        }
        return count;
    }

    private String removeGuavaListFactoryCasts(String source) {
        String processed = source.replaceAll("Lists\\.newArrayList\\(\\s*\\(Object\\[\\]\\)\\s*",
                "Lists.newArrayList(");
        return processed.replaceAll("Lists\\.newArrayList\\(\\s*\\(Iterable\\)\\s*", "Lists.newArrayList(");
    }

    private String restoreStringListLocalsFromFactoryAssignments(String source) {
        Matcher matcher = RAW_ARRAY_LIST_DECLARATION.matcher(source);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String localName = matcher.group(2);
            if (isAssignedFromStringListFactory(source, localName)) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                        matcher.group(1) + "java.util.List<String> " + localName + ";"));
            } else {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private boolean isAssignedFromStringListFactory(String source, String localName) {
        Pattern arraysAsList = Pattern.compile("\\b" + Pattern.quote(localName)
                + "\\s*=\\s*Arrays\\.asList\\s*\\(");
        Pattern annotationValues = Pattern.compile("\\b" + Pattern.quote(localName)
                + "\\s*=\\s*Lists\\.newArrayList\\s*\\(\\s*[^;\\n]*\\.value\\(\\)\\s*\\)");
        return arraysAsList.matcher(source).find() && annotationValues.matcher(source).find();
    }

    private String normalizeGenericType(String type) {
        return type.replaceAll("\\s+", " ").trim();
    }

    private String rawSimpleTypeName(String genericType) {
        int genericStart = genericType.indexOf('<');
        String rawType = genericStart >= 0 ? genericType.substring(0, genericStart).trim() : genericType.trim();
        int lastDot = rawType.lastIndexOf('.');
        return lastDot >= 0 ? rawType.substring(lastDot + 1) : rawType;
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

    private String unwrapSingleElementRedisSetOperationArrays(String source) {
        Pattern singleElementSetOperationArray = Pattern.compile("(opsForSet\\(\\)\\.(?:add|remove)\\s*"
                + "\\([^\\n;]*?,\\s*)(?:\\(Object\\[\\]\\)\\s*)?new\\s+(?:String|Object)\\[\\]\\s*"
                + "\\{\\s*([^{};]+?)\\s*}\\s*\\)");
        Matcher matcher = singleElementSetOperationArray.matcher(source);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                    matcher.group(1) + matcher.group(2).trim() + ")"));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String addListElementTypes(String source) {
        Matcher matcher = RAW_COLLECTION_ENHANCED_FOR.matcher(source);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String collectionType = matcher.group(1);
            String collectionName = matcher.group(2);
            String initializer = addConstructorElementType(matcher.group(3), collectionType, matcher.group(5).trim());
            String indent = matcher.group(4);
            String elementType = matcher.group(5).trim();
            String elementName = matcher.group(6);
            String replacement = collectionType + "<" + elementType + "> " + collectionName + " = " + initializer + ";\n"
                    + indent + "for (" + elementType + " " + elementName + " : " + collectionName + ")";
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String addConstructorElementType(String initializer, String collectionType, String elementType) {
        return initializer.replaceFirst("\\bnew\\s+" + Pattern.quote(collectionType) + "\\s*\\(",
                "new " + collectionType + "<" + elementType + ">(");
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

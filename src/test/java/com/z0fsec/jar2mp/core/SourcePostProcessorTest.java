package com.z0fsec.jar2mp.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourcePostProcessorTest {

    @Test
    void removesBroadObjectCasts() {
        String processed = new SourcePostProcessor().process(
                "redirectAttributes.addFlashAttribute(\"message\", (Object)\"ok\");\n"
                        + "repository.save((Object)owner);\n");

        assertFalse(processed.contains("(Object)"));
        assertTrue(processed.contains("repository.save(owner);"));
    }

    @Test
    void stripsDecompilerHeader() {
        String processed = new SourcePostProcessor().process(
                "/*\n"
                        + " * Decompiled with CFR 0.152.\n"
                        + " * \n"
                        + " * Could not load the following classes:\n"
                        + " *  demo.Dependency\n"
                        + " */\n"
                        + "package demo;\n\npublic class Sample {}\n",
                "demo.Sample");

        assertFalse(processed.contains("Decompiled with"));
        assertTrue(processed.startsWith("package demo;"));
    }

    @Test
    void removesImplicitImports() {
        String processed = new SourcePostProcessor().process(
                "package demo;\n\n"
                        + "import demo.Helper;\n"
                        + "import java.lang.String;\n"
                        + "import demo.nested.Other;\n"
                        + "import java.util.List;\n\n"
                        + "public class Sample {}\n",
                "demo.Sample");

        assertFalse(processed.contains("import demo.Helper;"));
        assertFalse(processed.contains("import java.lang.String;"));
        assertTrue(processed.contains("import demo.nested.Other;"));
        assertTrue(processed.contains("import java.util.List;"));
    }

    @Test
    void removesParameterArrayCasts() {
        String processed = new SourcePostProcessor().process(
                "public static void main(String[] args) {\n"
                        + "        run((String[])args);\n"
                        + "}\n");

        assertTrue(processed.contains("run(args);"));
        assertFalse(processed.contains("(String[])args"));
    }

    @Test
    void addsListElementTypeWhenEnhancedForUsesTypedElement() {
        String processed = new SourcePostProcessor().process(
                "List findPetTypes = this.types.findPetTypes();\n"
                        + "        for (PetType type : findPetTypes) {\n"
                        + "        }\n");

        assertTrue(processed.contains("List<PetType> findPetTypes = this.types.findPetTypes();"));
        assertTrue(processed.contains("for (PetType type : findPetTypes)"));
    }

    @Test
    void addsOptionalElementTypeAndRemovesOrElseThrowCast() {
        String processed = new SourcePostProcessor().process(
                "Optional optionalOwner = this.owners.findById(Integer.valueOf(ownerId));\n"
                        + "        Owner owner = (Owner)optionalOwner.orElseThrow(() -> new IllegalArgumentException(\"missing\"));\n");

        assertTrue(processed.contains("Optional<Owner> optionalOwner = this.owners.findById(Integer.valueOf(ownerId));"));
        assertTrue(processed.contains("Owner owner = optionalOwner.orElseThrow"));
        assertFalse(processed.contains("(Owner)optionalOwner"));
    }

    @Test
    void replacesUnavailableAnonymousInnerClassPlaceholder() {
        String processed = new SourcePostProcessor().process(
                "private static final Runnable RUNNABLE = new /* Unavailable Anonymous Inner Class!! */;\n");

        assertTrue(processed.contains("RUNNABLE = null;"));
        assertFalse(processed.contains("Unavailable Anonymous Inner Class"));
    }

    @Test
    void preservesSyntheticSwitchMapReferenceInsteadOfChangingCaseSemantics() {
        String processed = new SourcePostProcessor().process(
                "switch (1.$SwitchMap$com$example$Mode[value.getMode().ordinal()]) {\n"
                        + "    case 1: break;\n"
                        + "}\n");

        assertTrue(processed.contains("1.$SwitchMap$com$example$Mode[value.getMode().ordinal()]"));
        assertFalse(processed.contains("switch (value.getMode().ordinal())"));
    }

    @Test
    void replacesNumericAnonymousClassFragments() {
        String processed = new SourcePostProcessor().process(
                "    private static final Runnable R = new 1();\n"
                        + "    private static final Handle H = new LocalPoolHandle((Pool) null, (1) null);\n"
                        + "    void add() {\n"
                        + "        2 strategy = null;\n"
                        + "    }\n");

        assertTrue(processed.contains("R = null;"));
        assertTrue(processed.contains("new LocalPoolHandle((Pool) null, null)"));
        assertTrue(processed.contains("Object strategy = null;"));
        assertFalse(processed.contains("new 1("));
        assertFalse(processed.contains("(1) null"));
    }

    @Test
    void balancesNullArgumentStatementsAfterAnonymousPlaceholderReplacement() {
        String processed = new SourcePostProcessor().process(
                "seedGeneratorThread.setUncaughtExceptionHandler(null;\n"
                        + "Object ret = AccessController.doPrivileged((PrivilegedAction<Object>) null;\n");

        assertTrue(processed.contains("setUncaughtExceptionHandler(null);"));
        assertTrue(processed.contains("doPrivileged((PrivilegedAction<Object>) null);"));
    }
}

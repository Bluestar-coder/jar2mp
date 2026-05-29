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
    void stripsOnlyDecompilerHeaderAndPreservesPackageImports() {
        String processed = new SourcePostProcessor().process(
                "/*\n"
                        + " * Decompiled with CFR.\n"
                        + " */\n"
                        + "package demo;\n\n"
                        + "import java.util.List;\n\n"
                        + "/*\n"
                        + " * This class specifies class file version 49.0.\n"
                        + " */\n"
                        + "public class Sample { List<String> values; }\n",
                "demo.Sample");

        assertTrue(processed.startsWith("package demo;"));
        assertTrue(processed.contains("import java.util.List;"));
        assertTrue(processed.contains("public class Sample"));
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
    void replacesDuplicateAnonymousInnerClassPlaceholder() {
        String processed = new SourcePostProcessor().process(
                "worker.execute(new /* invalid duplicate definition of identical inner class */);\n");

        assertTrue(processed.contains("worker.execute(null);"));
        assertFalse(processed.contains("invalid duplicate definition"));
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
    void replacesCfrVoidTemporaryLocalDeclarations() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "  void run() {\n"
                        + "    void var15_20;\n"
                        + "    System.out.println(1);\n"
                        + "  }\n"
                        + "}\n",
                "Sample");

        assertFalse(processed.contains("void var15_20;"));
        assertTrue(processed.contains("Object var15_20 = null;"));
    }

    @Test
    void shortensQualifiedInnerClassInstanceCreation() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    void run(Ansi ansi, Handler handler, Method method) {\n"
                        + "        Ansi.Text text = ansi.new Ansi.Text(0);\n"
                        + "        Handler.Binding binding = handler.new Handler.Binding(method);\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("ansi.new Text(0);"));
        assertTrue(processed.contains("handler.new Binding(method);"));
        assertFalse(processed.contains("ansi.new Ansi.Text"));
        assertFalse(processed.contains("handler.new Handler.Binding"));
    }

    @Test
    void infersNumericGenericPlaceholderFromEnhancedForElementType() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    void run() {\n"
                        + "        ArrayList<1> bookKeeping = new ArrayList<1>();\n"
                        + "        bookKeeping.add(new Runnable() { public void run() {} });\n"
                        + "        for (Runnable runnable : bookKeeping) {\n"
                        + "            runnable.run();\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("ArrayList<Runnable> bookKeeping = new ArrayList<Runnable>();"));
        assertFalse(processed.contains("ArrayList<1>"));
    }

    @Test
    void removesWildcardBoundsFromLambdaParameterList() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    void run(ExtendedAttributes<K, V> attributes) {\n"
                        + "        attributes.forEach((? super K extendedAttributeKey, ? super V value) -> {\n"
                        + "            use(extendedAttributeKey, value);\n"
                        + "        });\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("attributes.forEach((extendedAttributeKey, value) -> {"));
        assertFalse(processed.contains("? super"));
    }

    @Test
    void balancesNullArgumentStatementsAfterAnonymousPlaceholderReplacement() {
        String processed = new SourcePostProcessor().process(
                "seedGeneratorThread.setUncaughtExceptionHandler(null;\n"
                        + "Object ret = AccessController.doPrivileged((PrivilegedAction<Object>) null;\n");

        assertTrue(processed.contains("setUncaughtExceptionHandler(null);"));
        assertTrue(processed.contains("doPrivileged((PrivilegedAction<Object>) null);"));
    }

    @Test
    void castsDoPrivilegedMethodReferenceToPrivilegedAction() {
        String processed = new SourcePostProcessor().process(
                "import java.security.AccessController;\n"
                        + "import java.security.PrivilegedAction;\n"
                        + "class Sample {\n"
                        + "    ClassLoader get() {\n"
                        + "        return AccessController.doPrivileged(Sample::directGetContextClassLoader);\n"
                        + "    }\n"
                        + "    static ClassLoader directGetContextClassLoader() { return null; }\n"
                        + "}\n");

        assertTrue(processed.contains(
                "AccessController.doPrivileged((PrivilegedAction<ClassLoader>) Sample::directGetContextClassLoader)"));
    }

    @Test
    void castsDoPrivilegedLambdaToPrivilegedActionUsingEnclosingReturnType() {
        String processed = new SourcePostProcessor().process(
                "import java.security.AccessController;\n"
                        + "import java.security.PrivilegedAction;\n"
                        + "class Sample {\n"
                        + "    private static ClassLoader getContextClassLoaderInternal() throws Exception {\n"
                        + "        return AccessController.doPrivileged(() -> Sample.directGetContextClassLoader());\n"
                        + "    }\n"
                        + "    static ClassLoader directGetContextClassLoader() { return null; }\n"
                        + "}\n");

        assertTrue(processed.contains(
                "AccessController.doPrivileged((PrivilegedAction<ClassLoader>) () -> Sample.directGetContextClassLoader())"));
    }

    @Test
    void castsDoPrivilegedInsideIfBlockUsingEnclosingMethodReturnType() {
        String processed = new SourcePostProcessor().process(
                "import java.nio.file.Files;\n"
                        + "import java.nio.file.Path;\n"
                        + "import java.security.AccessController;\n"
                        + "import java.security.PrivilegedAction;\n"
                        + "class Sample {\n"
                        + "    static Boolean isSymbolicLink(Path file) {\n"
                        + "        if (System.getSecurityManager() == null) {\n"
                        + "            return Files.isSymbolicLink(file);\n"
                        + "        }\n"
                        + "        return AccessController.doPrivileged(() -> Files.isSymbolicLink(file));\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains(
                "AccessController.doPrivileged((PrivilegedAction<Boolean>) () -> Files.isSymbolicLink(file))"));
        assertFalse(processed.contains("PrivilegedAction<if>"));
    }

    @Test
    void castsDoPrivilegedInsideGenericMethodUsingGenericReturnType() {
        String processed = new SourcePostProcessor().process(
                "import java.security.AccessController;\n"
                        + "import java.security.PrivilegedAction;\n"
                        + "class Sample {\n"
                        + "    private static <T> T doPrivileged(PrivilegedAction<T> action) {\n"
                        + "        return AccessController.doPrivileged(action);\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains(
                "AccessController.doPrivileged((PrivilegedAction<T>) action)"));
        assertFalse(processed.contains("PrivilegedAction<<T> T>"));
    }

    @Test
    void removesUnreachableBreakAfterInfiniteLoopThatAlreadyReturnsOrThrows() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    Object row() {\n"
                        + "        while (true) {\n"
                        + "            if (done()) {\n"
                        + "                return new Object();\n"
                        + "            }\n"
                        + "            throw new RuntimeException();\n"
                        + "        }\n"
                        + "        break;\n"
                        + "    }\n"
                        + "}\n");

        assertFalse(processed.contains("        break;\n"));
        assertTrue(processed.contains("while (true)"));
    }
}

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
}

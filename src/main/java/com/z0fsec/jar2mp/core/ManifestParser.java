package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.ManifestInfo;

import java.util.jar.Attributes;
import java.util.jar.Manifest;

public class ManifestParser {

    public ManifestInfo parse(Manifest manifest) {
        if (manifest == null) return null;

        ManifestInfo info = new ManifestInfo();
        Attributes mainAttrs = manifest.getMainAttributes();

        info.setMainClass(getAttr(mainAttrs, "Main-Class"));
        info.setImplementationTitle(getAttr(mainAttrs, "Implementation-Title"));
        info.setImplementationVersion(getAttr(mainAttrs, "Implementation-Version"));
        info.setImplementationVendor(getAttr(mainAttrs, "Implementation-Vendor"));
        info.setImplementationVendorId(getAttr(mainAttrs, "Implementation-Vendor-Id"));
        info.setBundleSymbolicName(getAttr(mainAttrs, "Bundle-SymbolicName"));
        info.setBundleVersion(getAttr(mainAttrs, "Bundle-Version"));
        info.setAutomaticModuleName(getAttr(mainAttrs, "Automatic-Module-Name"));
        info.setCreatedBy(getAttr(mainAttrs, "Created-By"));
        info.setBuildJdk(getAttr(mainAttrs, "Build-Jdk"));
        info.setClassPath(getAttr(mainAttrs, "Class-Path"));

        // Store all entries for display
        for (Object key : mainAttrs.keySet()) {
            String keyStr = key.toString();
            String value = mainAttrs.getValue(keyStr);
            if (value != null) {
                info.addEntry(keyStr, value);
            }
        }

        return info;
    }

    private String getAttr(Attributes attrs, String name) {
        String value = attrs.getValue(name);
        return (value != null && !value.trim().isEmpty()) ? value.trim() : null;
    }
}

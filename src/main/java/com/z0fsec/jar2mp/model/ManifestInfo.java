package com.z0fsec.jar2mp.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class ManifestInfo {
    private String mainClass;
    private String implementationTitle;
    private String implementationVersion;
    private String implementationVendor;
    private String implementationVendorId;
    private String bundleSymbolicName;
    private String bundleVersion;
    private String automaticModuleName;
    private String createdBy;
    private String buildJdk;
    private String classPath;
    private final Map<String, String> allEntries = new LinkedHashMap<>();

    public String getMainClass() { return mainClass; }
    public void setMainClass(String mainClass) { this.mainClass = mainClass; }
    public String getImplementationTitle() { return implementationTitle; }
    public void setImplementationTitle(String implementationTitle) { this.implementationTitle = implementationTitle; }
    public String getImplementationVersion() { return implementationVersion; }
    public void setImplementationVersion(String implementationVersion) { this.implementationVersion = implementationVersion; }
    public String getImplementationVendor() { return implementationVendor; }
    public void setImplementationVendor(String implementationVendor) { this.implementationVendor = implementationVendor; }
    public String getImplementationVendorId() { return implementationVendorId; }
    public void setImplementationVendorId(String implementationVendorId) { this.implementationVendorId = implementationVendorId; }
    public String getBundleSymbolicName() { return bundleSymbolicName; }
    public void setBundleSymbolicName(String bundleSymbolicName) { this.bundleSymbolicName = bundleSymbolicName; }
    public String getBundleVersion() { return bundleVersion; }
    public void setBundleVersion(String bundleVersion) { this.bundleVersion = bundleVersion; }
    public String getAutomaticModuleName() { return automaticModuleName; }
    public void setAutomaticModuleName(String automaticModuleName) { this.automaticModuleName = automaticModuleName; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getBuildJdk() { return buildJdk; }
    public void setBuildJdk(String buildJdk) { this.buildJdk = buildJdk; }
    public String getClassPath() { return classPath; }
    public void setClassPath(String classPath) { this.classPath = classPath; }
    public Map<String, String> getAllEntries() { return allEntries; }

    public void addEntry(String key, String value) {
        allEntries.put(key, value);
    }
}

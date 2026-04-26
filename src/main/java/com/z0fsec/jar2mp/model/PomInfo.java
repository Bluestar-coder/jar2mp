package com.z0fsec.jar2mp.model;

import java.util.ArrayList;
import java.util.List;

public class PomInfo {
    private String groupId;
    private String artifactId;
    private String version;
    private String packaging;
    private String parentGroupId;
    private String parentArtifactId;
    private String parentVersion;
    private final List<MavenDependency> dependencies = new ArrayList<>();

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public String getArtifactId() { return artifactId; }
    public void setArtifactId(String artifactId) { this.artifactId = artifactId; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getPackaging() { return packaging; }
    public void setPackaging(String packaging) { this.packaging = packaging; }
    public String getParentGroupId() { return parentGroupId; }
    public void setParentGroupId(String parentGroupId) { this.parentGroupId = parentGroupId; }
    public String getParentArtifactId() { return parentArtifactId; }
    public void setParentArtifactId(String parentArtifactId) { this.parentArtifactId = parentArtifactId; }
    public String getParentVersion() { return parentVersion; }
    public void setParentVersion(String parentVersion) { this.parentVersion = parentVersion; }
    public List<MavenDependency> getDependencies() { return dependencies; }

    public boolean hasCoordinates() {
        return groupId != null && !groupId.isEmpty()
                && artifactId != null && !artifactId.isEmpty()
                && version != null && !version.isEmpty();
    }
}

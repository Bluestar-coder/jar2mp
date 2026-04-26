package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.*;

import java.util.List;

public class PomGenerator {

    public String generate(JarAnalysisResult analysis, ProjectConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n");
        sb.append("         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        sb.append("         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n");
        sb.append("    <modelVersion>4.0.0</modelVersion>\n\n");

        // GAV
        String groupId = config.getGroupId() != null ? config.getGroupId() : analysis.getDetectedGroupId();
        String artifactId = config.getArtifactId() != null ? config.getArtifactId() : analysis.getDetectedArtifactId();
        String version = config.getVersion() != null ? config.getVersion() : analysis.getDetectedVersion();

        sb.append("    <groupId>").append(escapeXml(groupId)).append("</groupId>\n");
        sb.append("    <artifactId>").append(escapeXml(artifactId)).append("</artifactId>\n");
        sb.append("    <version>").append(escapeXml(version)).append("</version>\n");

        // Packaging
        String packaging = config.getPackaging();
        if (packaging == null) {
            packaging = analysis.isWar() ? "war" : "jar";
        }
        if (!"jar".equals(packaging)) {
            sb.append("    <packaging>").append(escapeXml(packaging)).append("</packaging>\n");
        }

        sb.append("\n");

        // Properties
        int javaVersion = config.getJavaVersion() > 0 ? config.getJavaVersion() : analysis.getJavaVersion();
        sb.append("    <properties>\n");
        sb.append("        <maven.compiler.source>").append(javaVersion).append("</maven.compiler.source>\n");
        sb.append("        <maven.compiler.target>").append(javaVersion).append("</maven.compiler.target>\n");
        sb.append("        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>\n");
        sb.append("    </properties>\n\n");

        // Dependencies
        List<MavenDependency> deps = analysis.getDetectedDependencies();
        List<MavenDependency> included = new java.util.ArrayList<>();
        for (MavenDependency dep : deps) {
            if (dep.isIncluded()) {
                included.add(dep);
            }
        }

        if (!included.isEmpty()) {
            sb.append("    <dependencies>\n");
            for (MavenDependency dep : included) {
                sb.append("        <dependency>\n");
                sb.append("            <groupId>").append(escapeXml(dep.getGroupId())).append("</groupId>\n");
                sb.append("            <artifactId>").append(escapeXml(dep.getArtifactId())).append("</artifactId>\n");
                sb.append("            <version>").append(escapeXml(dep.getVersion())).append("</version>\n");
                if (dep.getScope() != null && !"compile".equals(dep.getScope())) {
                    sb.append("            <scope>").append(escapeXml(dep.getScope())).append("</scope>\n");
                }
                sb.append("        </dependency>\n");
            }
            sb.append("    </dependencies>\n\n");
        }

        // Build section
        sb.append("    <build>\n");
        sb.append("        <plugins>\n");
        sb.append("            <plugin>\n");
        sb.append("                <groupId>org.apache.maven.plugins</groupId>\n");
        sb.append("                <artifactId>maven-compiler-plugin</artifactId>\n");
        sb.append("                <version>3.11.0</version>\n");
        sb.append("                <configuration>\n");
        sb.append("                    <source>").append(javaVersion).append("</source>\n");
        sb.append("                    <target>").append(javaVersion).append("</target>\n");
        sb.append("                    <encoding>UTF-8</encoding>\n");
        sb.append("                </configuration>\n");
        sb.append("            </plugin>\n");
        sb.append("        </plugins>\n");
        sb.append("    </build>\n");

        sb.append("</project>\n");
        return sb.toString();
    }

    private String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}

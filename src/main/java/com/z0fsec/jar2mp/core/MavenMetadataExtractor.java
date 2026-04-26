package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.MavenDependency;
import com.z0fsec.jar2mp.model.PomInfo;

import java.io.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.*;

public class MavenMetadataExtractor {

    public PomInfo extract(JarFile jarFile) {
        PomInfo pomInfo = null;

        // 1. Look for pom.properties
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (name.startsWith("META-INF/maven/") && name.endsWith("pom.properties")) {
                pomInfo = parsePomProperties(jarFile, entry);
                break;
            }
        }

        // 2. Look for embedded pom.xml
        entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (name.startsWith("META-INF/maven/") && name.endsWith("pom.xml") && !name.contains("/target/")) {
                PomInfo pomXmlInfo = parsePomXml(jarFile, entry);
                if (pomXmlInfo != null) {
                    if (pomInfo == null) {
                        pomInfo = pomXmlInfo;
                    } else {
                        // Merge: pom.xml may have dependency info that pom.properties doesn't
                        if (pomInfo.getGroupId() == null) pomInfo.setGroupId(pomXmlInfo.getGroupId());
                        if (pomInfo.getArtifactId() == null) pomInfo.setArtifactId(pomXmlInfo.getArtifactId());
                        if (pomInfo.getVersion() == null) pomInfo.setVersion(pomXmlInfo.getVersion());
                        for (MavenDependency dep : pomXmlInfo.getDependencies()) {
                            if (!containsDep(pomInfo.getDependencies(), dep)) {
                                dep.setConfidence(MavenDependency.Confidence.HIGH);
                                pomInfo.getDependencies().add(dep);
                            }
                        }
                    }
                }
                break;
            }
        }

        return pomInfo;
    }

    private PomInfo parsePomProperties(JarFile jarFile, JarEntry entry) {
        try (InputStream is = jarFile.getInputStream(entry)) {
            Properties props = new Properties();
            props.load(is);

            PomInfo info = new PomInfo();
            info.setGroupId(props.getProperty("groupId"));
            info.setArtifactId(props.getProperty("artifactId"));
            info.setVersion(props.getProperty("version"));

            // Extract parent path info as fallback
            String path = entry.getName();
            // META-INF/maven/{groupId}/{artifactId}/pom.properties
            String[] parts = path.split("/");
            if (parts.length >= 4) {
                if (info.getGroupId() == null) info.setGroupId(parts[2]);
                if (info.getArtifactId() == null) info.setArtifactId(parts[3]);
            }

            return info;
        } catch (IOException e) {
            return null;
        }
    }

    private PomInfo parsePomXml(JarFile jarFile, JarEntry entry) {
        try (InputStream is = jarFile.getInputStream(entry)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(is);
            doc.getDocumentElement().normalize();

            PomInfo info = new PomInfo();
            Element root = doc.getDocumentElement();

            info.setGroupId(getTextContent(root, "groupId"));
            info.setArtifactId(getTextContent(root, "artifactId"));
            info.setVersion(getTextContent(root, "version"));
            info.setPackaging(getTextContent(root, "packaging"));

            // Parent POM
            NodeList parentNodes = root.getElementsByTagName("parent");
            if (parentNodes.getLength() > 0) {
                Element parent = (Element) parentNodes.item(0);
                info.setParentGroupId(getTextContent(parent, "groupId"));
                info.setParentArtifactId(getTextContent(parent, "artifactId"));
                info.setParentVersion(getTextContent(parent, "version"));
            }

            // If own coordinates are missing, inherit from parent
            if (info.getGroupId() == null) info.setGroupId(info.getParentGroupId());
            if (info.getVersion() == null) info.setVersion(info.getParentVersion());
            if (info.getPackaging() == null) info.setPackaging("jar");

            // Dependencies
            NodeList depsNodes = root.getElementsByTagName("dependency");
            for (int i = 0; i < depsNodes.getLength(); i++) {
                Element depElem = (Element) depsNodes.item(i);
                // Make sure this is a direct child of <dependencies>
                Node parent2 = depElem.getParentNode();
                if (parent2 == null || !"dependencies".equals(parent2.getNodeName())) continue;

                String dg = getTextContent(depElem, "groupId");
                String da = getTextContent(depElem, "artifactId");
                String dv = getTextContent(depElem, "version");
                String ds = getTextContent(depElem, "scope");

                MavenDependency dep = new MavenDependency(dg, da, dv != null ? dv : "unknown",
                        MavenDependency.Confidence.HIGH);
                if (ds != null && !ds.isEmpty()) {
                    dep.setScope(ds);
                }
                info.getDependencies().add(dep);
            }

            return info;
        } catch (Exception e) {
            return null;
        }
    }

    private String getTextContent(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            String text = nodes.item(0).getTextContent();
            return (text != null && !text.trim().isEmpty()) ? text.trim() : null;
        }
        return null;
    }

    private boolean containsDep(List<MavenDependency> deps, MavenDependency dep) {
        for (MavenDependency d : deps) {
            if (d.getKey().equals(dep.getKey())) return true;
        }
        return false;
    }
}

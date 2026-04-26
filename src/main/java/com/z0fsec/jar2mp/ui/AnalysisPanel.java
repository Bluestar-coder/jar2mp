package com.z0fsec.jar2mp.ui;

import com.z0fsec.jar2mp.core.*;
import com.z0fsec.jar2mp.db.PackagePrefixDatabase;
import com.z0fsec.jar2mp.model.*;
import com.z0fsec.jar2mp.util.IoUtils;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.util.List;
import java.util.function.Consumer;

public class AnalysisPanel extends BasePanel {

    private JTable summaryTable;
    private JTable manifestTable;
    private DefaultTableModel summaryModel;
    private DefaultTableModel manifestModel;
    private JLabel mavenInfoLabel;

    public AnalysisPanel(Consumer<String> logConsumer) {
        super(logConsumer);
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout());

        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // JAR Structure Summary
        JPanel summaryPanel = new JPanel(new BorderLayout());
        summaryPanel.setBorder(BorderFactory.createTitledBorder("JAR 结构"));
        summaryModel = new DefaultTableModel(new Object[]{"属性", "值"}, 0) {
            public boolean isCellEditable(int row, int col) { return false; }
        };
        summaryTable = new JTable(summaryModel);
        summaryTable.setRowHeight(24);
        summaryPanel.add(new JScrollPane(summaryTable), BorderLayout.CENTER);
        contentPanel.add(summaryPanel, BorderLayout.NORTH);

        // Center: manifest + maven info
        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));

        JPanel manifestPanel = new JPanel(new BorderLayout());
        manifestPanel.setBorder(BorderFactory.createTitledBorder("MANIFEST.MF"));
        manifestModel = new DefaultTableModel(new Object[]{"键", "值"}, 0) {
            public boolean isCellEditable(int row, int col) { return false; }
        };
        manifestTable = new JTable(manifestModel);
        manifestTable.setRowHeight(24);
        manifestPanel.add(new JScrollPane(manifestTable), BorderLayout.CENTER);
        centerPanel.add(manifestPanel, BorderLayout.CENTER);

        JPanel mavenPanel = new JPanel(new BorderLayout());
        mavenPanel.setBorder(BorderFactory.createTitledBorder("嵌入的 Maven 信息"));
        mavenInfoLabel = new JLabel("未发现嵌入的 Maven 元数据");
        mavenInfoLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        mavenPanel.add(mavenInfoLabel, BorderLayout.CENTER);
        centerPanel.add(mavenPanel, BorderLayout.SOUTH);

        contentPanel.add(centerPanel, BorderLayout.CENTER);
        add(contentPanel, BorderLayout.CENTER);
    }

    public void updateAnalysis(JarAnalysisResult result) {
        // Clear old data
        summaryModel.setRowCount(0);
        manifestModel.setRowCount(0);

        if (result == null) return;

        // Summary
        summaryModel.addRow(new Object[]{"文件", result.getSourceFile().getName()});
        summaryModel.addRow(new Object[]{"类型", result.isWar() ? "WAR" : "JAR"});
        summaryModel.addRow(new Object[]{"总条目数", result.getTotalEntries()});
        summaryModel.addRow(new Object[]{"类文件数", result.getClassFiles().size()});
        summaryModel.addRow(new Object[]{"资源文件数", result.getResourceFiles().size()});
        summaryModel.addRow(new Object[]{"Java 版本", "Java " + result.getJavaVersion()});
        summaryModel.addRow(new Object[]{"检测到 GroupId", result.getDetectedGroupId()});
        summaryModel.addRow(new Object[]{"检测到 ArtifactId", result.getDetectedArtifactId()});
        summaryModel.addRow(new Object[]{"检测到 Version", result.getDetectedVersion()});
        summaryModel.addRow(new Object[]{"检测到依赖数", result.getDetectedDependencies().size()});

        // Manifest
        if (result.getManifestInfo() != null) {
            for (java.util.Map.Entry<String, String> entry : result.getManifestInfo().getAllEntries().entrySet()) {
                manifestModel.addRow(new Object[]{entry.getKey(), entry.getValue()});
            }
        }

        // Maven info
        PomInfo pomInfo = result.getEmbeddedPomInfo();
        if (pomInfo != null && pomInfo.hasCoordinates()) {
            mavenInfoLabel.setText(String.format("<html><b>groupId:</b> %s &nbsp;&nbsp; " +
                            "<b>artifactId:</b> %s &nbsp;&nbsp; <b>version:</b> %s</html>",
                    pomInfo.getGroupId(), pomInfo.getArtifactId(), pomInfo.getVersion()));
        } else {
            mavenInfoLabel.setText("未发现嵌入的 Maven 元数据");
        }

        appendSuccess("分析结果已加载");
    }

    public void clearData() {
        summaryModel.setRowCount(0);
        manifestModel.setRowCount(0);
        mavenInfoLabel.setText("未发现嵌入的 Maven 元数据");
    }
}

/*
 * Copyright 2019 Philipp Salvisberg <philipp.salvisberg@trivadis.com>
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.utplsql.sqldev.ui.runner;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.LookAndFeel;
import javax.swing.RepaintManager;
import javax.swing.RowFilter;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.event.HyperlinkEvent;
import javax.swing.plaf.basic.BasicProgressBarUI;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;

import org.springframework.web.util.HtmlUtils;
import org.utplsql.sqldev.dal.UtplsqlDao;
import org.utplsql.sqldev.model.DatabaseTools;
import org.utplsql.sqldev.model.LimitedLinkedHashMap;
import org.utplsql.sqldev.model.StringTools;
import org.utplsql.sqldev.model.SystemTools;
import org.utplsql.sqldev.model.preference.PreferenceModel;
import org.utplsql.sqldev.model.runner.Counter;
import org.utplsql.sqldev.model.runner.Expectation;
import org.utplsql.sqldev.model.runner.Run;
import org.utplsql.sqldev.model.runner.Test;
import org.utplsql.sqldev.parser.UtplsqlParser;
import org.utplsql.sqldev.resources.UtplsqlResources;
import org.utplsql.sqldev.runner.UtplsqlRunner;
import org.utplsql.sqldev.runner.UtplsqlWorksheetRunner;

import oracle.dbtools.raptor.controls.grid.DefaultDrillLink;
import oracle.ide.config.Preferences;
import oracle.javatools.ui.table.ToolbarButton;

public class RunnerPanel {
    private static final Color GREEN = new Color(0, 153, 0);
    private static final Color RED = new Color(153, 0, 0);
    private static final int INDICATOR_WIDTH = 20;
    private static final int OVERVIEW_TABLE_ROW_HEIGHT = 20;
    private static final Dimension TEXTPANE_DIM = new Dimension(100, 100);

    private boolean useSmartTimes = false;
    private LimitedLinkedHashMap<String, Run> runs = new LimitedLinkedHashMap<>(10);
    private Run currentRun;
    private JPanel basePanel;
    private DefaultComboBoxModel<ComboBoxItem<String, String>> runComboBoxModel;
    private JComboBox<ComboBoxItem<String, String>> runComboBox;
    private JLabel statusLabel;
    private Timer elapsedTimeTimer;
    private JLabel testCounterValueLabel;
    private JLabel errorCounterValueLabel;
    private JLabel failureCounterValueLabel;
    private JLabel disabledCounterValueLabel;
    private JLabel warningsCounterValueLabel;
    private JLabel infoCounterValueLabel;
    private JCheckBoxMenuItem showDisabledCounterCheckBoxMenuItem;
    private JCheckBoxMenuItem showWarningsCounterCheckBoxMenuItem;
    private JCheckBoxMenuItem showInfoCounterCheckBoxMenuItem;
    private JProgressBar progressBar;
    private TestOverviewTableModel testOverviewTableModel;
    private JTable testOverviewTable;
    private JMenuItem testOverviewRunMenuItem;
    private JMenuItem testOverviewRunWorksheetMenuItem;
    private JCheckBoxMenuItem showTestDescriptionCheckBoxMenuItem;
    private JCheckBoxMenuItem showWarningIndicatorCheckBoxMenuItem;
    private JCheckBoxMenuItem showInfoIndicatorCheckBoxMenuItem;
    private JCheckBoxMenuItem showSuccessfulTestsCheckBoxMenuItem;
    private JCheckBoxMenuItem showDisabledTestsCheckBoxMenuItem;
    private JCheckBoxMenuItem syncDetailTabCheckBoxMenuItem;
    private RunnerTextField testOwnerTextField;
    private RunnerTextField testPackageTextField;
    private RunnerTextField testProcedureTextField;
    private RunnerTextArea testDescriptionTextArea;
    private RunnerTextArea testIdTextArea;
    private RunnerTextField testStartTextField;
    private FailuresTableModel failuresTableModel;
    private JTable failuresTable;
    private RunnerTextPane testFailureMessageTextPane;
    private RunnerTextPane testErrorStackTextPane;
    private RunnerTextPane testWarningsTextPane;
    private RunnerTextPane testServerOutputTextPane;
    private JTabbedPane testDetailTabbedPane;

    // used in multiple components, therefore an inner class
    private class TestTableHeaderRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 6295858563570577027L;

        @Override
        public Component getTableCellRendererComponent(final JTable table, final Object value, final boolean isSelected,
                final boolean hasFocus, final int row, final int col) {
            final TableCellRenderer renderer = table.getTableHeader().getDefaultRenderer();
            final JLabel label = ((JLabel) renderer.getTableCellRendererComponent(table, value, isSelected, hasFocus,
                    row, col));
            if (col == 0) {
                label.setIcon(UtplsqlResources.getIcon("STATUS_ICON"));
                label.setHorizontalAlignment(JLabel.CENTER);
            } else if (col == 1) {
                label.setIcon(UtplsqlResources.getIcon("WARNING_ICON"));
                label.setHorizontalAlignment(JLabel.CENTER);
            } else if (col == 2) {
                label.setIcon(UtplsqlResources.getIcon("INFO_ICON"));
                label.setHorizontalAlignment(JLabel.CENTER);
            } else if (col == 3) {
                label.setIcon(null);
                label.setHorizontalAlignment(JLabel.LEFT);
            } else if (col == 4) {
                label.setIcon(null);
                label.setHorizontalAlignment(JLabel.RIGHT);
            }
            return label;
        }
    }

    // used in mulitple components, therefore an inner class
    private class FailuresTableHeaderRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 5059401447983514596L;

        @Override
        public Component getTableCellRendererComponent(final JTable table, final Object value, final boolean isSelected,
                final boolean hasFocus, final int row, final int col) {
            final TableCellRenderer renderer = table.getTableHeader().getDefaultRenderer();
            final JLabel label = ((JLabel) renderer.getTableCellRendererComponent(table, value, isSelected, hasFocus,
                    row, col));
            if (col == 0) {
                label.setHorizontalAlignment(JLabel.RIGHT);
            } else {
                label.setHorizontalAlignment(JLabel.LEFT);
            }
            return label;
        }
    }

    public Component getGUI() {
        if (basePanel == null) {
            initializeGUI();
        }
        if (!basePanel.isShowing()) {
            applyPreferences();
        }
        return basePanel;
    }

    private void resetDerived() {
        testOverviewTable.getRowSorter().setSortKeys(null);
        testOverviewRunMenuItem.setEnabled(false);
        testOverviewRunWorksheetMenuItem.setEnabled(false);
        testIdTextArea.setText(null);
        testOwnerTextField.setText(null);
        testPackageTextField.setText(null);
        testProcedureTextField.setText(null);
        testDescriptionTextArea.setText(null);
        testStartTextField.setText(null);
        failuresTableModel.setModel(null);
        failuresTableModel.fireTableDataChanged();
        testFailureMessageTextPane.setText(null);
        testErrorStackTextPane.setText(null);
        testWarningsTextPane.setText(null);
        testServerOutputTextPane.setText(null);
    }

    private void refreshRunsComboBox() {
        if (!runs.isEmpty()) {
            for (ActionListener al : runComboBox.getActionListeners()) {
                runComboBox.removeActionListener(al);
            }
            runComboBoxModel.removeAllElements();
            List<Map.Entry<String, Run>> entries = new ArrayList<>(runs.entrySet());
            for (int i = runs.size() - 1; i >= 0; i--) {
                final Map.Entry<String, Run> entry = entries.get(i);
                final ComboBoxItem<String, String> item = new ComboBoxItem<>(entry.getKey(), entry.getValue().getName());
                runComboBoxModel.addElement(item);
            }
            runComboBox.setSelectedIndex(0);
            runComboBox.addActionListener(event -> comboBoxAction());
        }
    }

    private void applyShowNumberOfRunsInHistory(final int maxRuns) {
        if (maxRuns != runs.getMaxEntries()) {
            final LimitedLinkedHashMap<String, Run> newRuns = new LimitedLinkedHashMap<>(maxRuns);
            for (final Map.Entry<String, Run> entry : runs.entrySet()) {
                newRuns.put(entry.getKey(), entry.getValue());
            }
           runs = newRuns;
        }
    }

    private void applyShowDisabledCounter() {
        disabledCounterValueLabel.getParent().setVisible(showDisabledCounterCheckBoxMenuItem.isSelected());
    }

    private void applyShowWarningsCounter() {
        warningsCounterValueLabel.getParent().setVisible(showWarningsCounterCheckBoxMenuItem.isSelected());
    }

    private void applyShowInfoCounter() {
        infoCounterValueLabel.getParent().setVisible(showInfoCounterCheckBoxMenuItem.isSelected());
    }

    private void applyShowTestDescription() {
        testOverviewTableModel.updateModel(showTestDescriptionCheckBoxMenuItem.isSelected());
        final TableColumn idColumn = testOverviewTable.getColumnModel().getColumn(3);
        idColumn.setHeaderValue(testOverviewTableModel.getTestIdColumnName());
        testOverviewTable.getTableHeader().repaint();
    }

    private void applyShowWarningIndicator(final boolean show) {
        final TableColumn col = testOverviewTable.getColumnModel().getColumn(1);
        if (show) {
            col.setWidth(INDICATOR_WIDTH);
            col.setMinWidth(INDICATOR_WIDTH);
            col.setMaxWidth(INDICATOR_WIDTH);
            col.setPreferredWidth(INDICATOR_WIDTH);
        } else {
            col.setWidth(0);
            col.setMinWidth(0);
            col.setMaxWidth(0);
            col.setPreferredWidth(0);
        }
    }

    private void applyShowInfoIndicator(final boolean show) {
        final TableColumn col = testOverviewTable.getColumnModel().getColumn(2);
        if (show) {
            col.setWidth(INDICATOR_WIDTH);
            col.setMinWidth(INDICATOR_WIDTH);
            col.setMaxWidth(INDICATOR_WIDTH);
            col.setPreferredWidth(INDICATOR_WIDTH);
        } else {
            col.setWidth(0);
            col.setMinWidth(0);
            col.setMaxWidth(0);
            col.setPreferredWidth(0);
        }
    }

    private void applyFilter(final boolean showSuccessfulTests, final boolean showDisabledTests) {
        @SuppressWarnings("unchecked")
        final TableRowSorter<TestOverviewTableModel> sorter = ((TableRowSorter<TestOverviewTableModel>) testOverviewTable.getRowSorter());
        final RowFilter<TestOverviewTableModel, Integer> filter = new RowFilter<TestOverviewTableModel, Integer>() {
            @Override
            public boolean include(final RowFilter.Entry<? extends TestOverviewTableModel, ? extends Integer> entry) {
                final Test test = entry.getModel().getTest((entry.getIdentifier()).intValue());
                final Counter counter = test.getCounter();
                if (counter != null) {
                    if (counter.getSuccess() > 0 && !showSuccessfulTests) {
                        return false;
                    }
                    if (counter.getDisabled() > 0 && !showDisabledTests) {
                        return false;
                    }
                }
                return true;
            }
        };
        sorter.setRowFilter(filter);
    }
    
    private void openTest(final Test test) {
            final UtplsqlDao dao = new UtplsqlDao(DatabaseTools.getConnection(currentRun.getConnectionName()));
            final String source = dao.getSource(test.getOwnerName(), "PACKAGE", test.getObjectName().toUpperCase()).trim();
            final UtplsqlParser parser = new UtplsqlParser(source);
            final int line = parser.getLineOf(test.getProcedureName());
            openEditor(test.getOwnerName(), "PACKAGE", test.getObjectName().toUpperCase(), line, 1);
    }

    private void openSelectedTest() {
        final int rowIndex = testOverviewTable.getSelectedRow();
        if (rowIndex != -1) {
            final int row = testOverviewTable.convertRowIndexToModel(rowIndex);
            final Test test = testOverviewTableModel.getTest(row);
            openTest(test);
        }
    }

    private void openSelectedFailure() {
        final int rowIndex = failuresTable.getSelectedRow();
        if (rowIndex != -1) {
            final int row = failuresTable.convertRowIndexToModel(rowIndex);
            final Expectation expectation = failuresTableModel.getExpectation(row);
            final Test test = testOverviewTableModel
                    .getTest(testOverviewTable.convertRowIndexToModel(testOverviewTable.getSelectedRow()));
            final Integer callerLine = expectation.getCallerLine();
            if (callerLine != null) {
                openEditor(test.getOwnerName(), "PACKAGE BODY", test.getObjectName().toUpperCase(),
                        expectation.getCallerLine(), 1);
            } else {
                openTest(test);
            }
        }
    }

    private String getHtml(final String text) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html>\n");
        sb.append("\t<head>\n");
        sb.append("\t\t<style type=\"text/css\">\n");
        sb.append("\t\t\tbody, p {font-family: ");
        sb.append(testOwnerTextField.getFont().getFamily());
        sb.append("; font-size: 1.0em; line-height: 1.1em; margin-top: 0px; margin-bottom: 0px;}\n");
        sb.append("\t\t</style>\n");
        sb.append("\t</head>\n");
        sb.append("\t<body>\n");
        sb.append("\t\t");
        sb.append(getLinkedAndFormattedText(text));
        sb.append('\n');
        sb.append("\t</body>\n");
        sb.append("</html>\n");
        return sb.toString();
    }

    private void openLink(final String link) {
        final String[] parts = link.split("/");
        final String type = parts[0];
        final String ownerName = parts[1];
        final String objectName = parts[2];
        int line = Integer.parseInt(parts[3]);
        final UtplsqlDao dao = new UtplsqlDao(DatabaseTools.getConnection(currentRun.getConnectionName()));
        final String objectType = "UNKNOWN".equals(type) ? dao.getObjectType(ownerName, objectName) : type;
        if (parts.length == 5) {
            final String procedureName = parts[4];
            final String source = dao.getSource(ownerName, objectType, objectName).trim();
            final UtplsqlParser parser = new UtplsqlParser(source);
            line = parser.getLineOf(procedureName);
        }
        openEditor(ownerName, objectType, objectName.toUpperCase(), line, 1);
    }

    private void openEditor(final String owner, final String type, final String name, final int line, final int col) {
        DefaultDrillLink drillLink = new DefaultDrillLink();
        drillLink.setConnName(currentRun.getConnectionName());
        // argument order is based on SQLDEV:LINK that can be used in SQL query result tables (editors, reports)
        drillLink.setArgs(new String[] { owner, type, name, String.valueOf(line), String.valueOf(col), "OpenEditor",
                "oracle.dbtools.raptor.controls.grid.DefaultDrillLink" });
        drillLink.performDrill();
    }

    private void syncDetailTab() {
        if (syncDetailTabCheckBoxMenuItem.isSelected()) {
            final int rowIndex = testOverviewTable.getSelectedRow();
            if (rowIndex != -1) {
                final int row = testOverviewTable.convertRowIndexToModel(rowIndex);
                final Test test = testOverviewTableModel.getTest(row);
                int tabIndex = 0;
                if (test != null && test.getCounter() != null) {
                    if (test.getCounter().getFailure() != null && test.getCounter().getFailure() > 0) {
                        tabIndex = 1;
                    } else if (test.getCounter().getError() != null && test.getCounter().getError() > 0) {
                        tabIndex = 2;
                    } else if (test.getCounter().getWarning() != null && test.getCounter().getWarning() > 0) {
                        tabIndex = 3;
                    } else if (test.getServerOutput() != null && test.getServerOutput().length() > 0) {
                        tabIndex = 4;
                    }
                }
                testDetailTabbedPane.setSelectedIndex(tabIndex);
            }
        }
    }

    private PreferenceModel getPreferenceModel() {
        PreferenceModel preferences = null;
        try {
            preferences = PreferenceModel.getInstance(Preferences.getPreferences());
        } catch (NoClassDefFoundError e) {
            // running outside of SQL Developer
            preferences = PreferenceModel.getInstance(null);
        }
        return preferences;
    }

    private void applyPreferences() {
        final PreferenceModel preferences = getPreferenceModel();
        applyShowNumberOfRunsInHistory(preferences.getNumberOfRunsInHistory());
        showDisabledCounterCheckBoxMenuItem.setSelected(preferences.isShowDisabledCounter());
        applyShowDisabledCounter();
        fixCheckBoxMenuItem(showDisabledCounterCheckBoxMenuItem);
        showWarningsCounterCheckBoxMenuItem.setSelected(preferences.isShowWarningsCounter());
        applyShowWarningsCounter();
        fixCheckBoxMenuItem(showWarningsCounterCheckBoxMenuItem);
        showInfoCounterCheckBoxMenuItem.setSelected(preferences.isShowInfoCounter());
        applyShowInfoCounter();
        fixCheckBoxMenuItem(showInfoCounterCheckBoxMenuItem);
        showTestDescriptionCheckBoxMenuItem.setSelected(preferences.isShowTestDescription());
        applyShowTestDescription();
        fixCheckBoxMenuItem(showTestDescriptionCheckBoxMenuItem);
        showWarningIndicatorCheckBoxMenuItem.setSelected(preferences.isShowWarningIndicator());
        applyShowWarningIndicator(showWarningIndicatorCheckBoxMenuItem.isSelected());
        fixCheckBoxMenuItem(showWarningIndicatorCheckBoxMenuItem);
        showInfoIndicatorCheckBoxMenuItem.setSelected(preferences.isShowInfoIndicator());
        applyShowInfoIndicator(showInfoIndicatorCheckBoxMenuItem.isSelected());
        showSuccessfulTestsCheckBoxMenuItem.setSelected(preferences.isShowSuccessfulTests());
        fixCheckBoxMenuItem(showSuccessfulTestsCheckBoxMenuItem);
        showDisabledTestsCheckBoxMenuItem.setSelected(preferences.isShowDisabledTests());
        fixCheckBoxMenuItem(showDisabledTestsCheckBoxMenuItem);
        applyFilter(showSuccessfulTestsCheckBoxMenuItem.isSelected(), showDisabledTestsCheckBoxMenuItem.isSelected());
        fixCheckBoxMenuItem(showInfoIndicatorCheckBoxMenuItem);
        syncDetailTabCheckBoxMenuItem.setSelected(preferences.isSyncDetailTab());
        fixCheckBoxMenuItem(syncDetailTabCheckBoxMenuItem);
        useSmartTimes = preferences.isUseSmartTimes();
    }

    public void setModel(final Run run) {
        runs.put(run.getReporterId(), run);
        refreshRunsComboBox();
        setCurrentRun(run);
    }

    private void setCurrentRun(final Run run) {
        if (run != currentRun) {
            currentRun = run;
            testOverviewTableModel.setModel(run.getTests(), showTestDescriptionCheckBoxMenuItem.isSelected(),
                    useSmartTimes);
            final String header = testOverviewTableModel.getTimeColumnName();
            final TableColumn timeColumn = testOverviewTable.getColumnModel().getColumn(4);
            if (!timeColumn.getHeaderValue().equals(header)) {
                timeColumn.setHeaderValue(header);
                testOverviewTable.getTableHeader().repaint();
            }
            resetDerived();
            final ComboBoxItem<String, String> item = new ComboBoxItem<>(currentRun.getReporterId(),
                    currentRun.getName());
            runComboBox.setSelectedItem(item);
            elapsedTimeTimer.start();
        }
    }

    public synchronized void update(final String reporterId) {
        setCurrentRun(runs.get(reporterId));
        final int row = currentRun.getCurrentTestNumber() - 1;
        final CharSequence header = testOverviewTableModel.getTestIdColumnName();
        final TableColumn idColumn = testOverviewTable.getColumnModel().getColumn(3);
        if (!idColumn.getHeaderValue().equals(header)) {
            idColumn.setHeaderValue(header);
            testOverviewTable.getTableHeader().repaint();
        }
        if (row < 0) {
            testOverviewTableModel.fireTableDataChanged();
        } else {
            if (testOverviewTableModel.getRowCount() > row) {
                final Rectangle positionOfCurrentTest = testOverviewTable
                        .getCellRect(testOverviewTable.convertRowIndexToView(row), 0, true);
                testOverviewTable.scrollRectToVisible(positionOfCurrentTest);
                testOverviewTableModel.fireTableRowsUpdated(row, row);
                SystemTools.sleep(5);
                if (!showSuccessfulTestsCheckBoxMenuItem.isSelected()
                        || !showDisabledTestsCheckBoxMenuItem.isSelected()) {
                    applyFilter(showSuccessfulTestsCheckBoxMenuItem.isSelected(),
                            showDisabledTestsCheckBoxMenuItem.isSelected());
                }
                testOverviewTable.scrollRectToVisible(positionOfCurrentTest);
            }
        }
        statusLabel.setText(currentRun.getStatus());
        testCounterValueLabel.setText(currentRun.getTotalNumberOfCompletedTests()
                + (currentRun.getTotalNumberOfTests() >= 0 ? "/" + currentRun.getTotalNumberOfTests() : ""));
        errorCounterValueLabel.setText(String.valueOf(currentRun.getCounter().getError()));
        failureCounterValueLabel.setText(String.valueOf(currentRun.getCounter().getFailure()));
        disabledCounterValueLabel.setText(String.valueOf(currentRun.getCounter().getDisabled()));
        warningsCounterValueLabel.setText(String.valueOf(currentRun.getCounter().getWarning()));
        infoCounterValueLabel.setText(String.valueOf(currentRun.getInfoCount()));
        if (currentRun.getTotalNumberOfTests() == 0) {
            progressBar.setValue(100);
        } else {
            progressBar
                    .setValue(100 * currentRun.getTotalNumberOfCompletedTests() / currentRun.getTotalNumberOfTests());
        }
        if (currentRun.getCounter().getError() > 0 || (currentRun.getCounter().getFailure() > 0)) {
            progressBar.setForeground(RED);
        } else {
            progressBar.setForeground(GREEN);
        }
    }

    private ArrayList<String> getPathListFromSelectedTests() {
        final ArrayList<String> pathList = new ArrayList<>();
        for (final int rowIndex : testOverviewTable.getSelectedRows()) {
            final int row = testOverviewTable.convertRowIndexToModel(rowIndex);
            final Test test = testOverviewTableModel.getTest(row);
            final String path = test.getOwnerName() + "." + test.getObjectName() + "." + test.getProcedureName();
            pathList.add(path);
        }
        return pathList;
    }

    private boolean isWindowsLookAndFeel() {
        LookAndFeel laf = UIManager.getLookAndFeel();
        final String lafName = laf != null ? laf.getName() : null;
        return "Windows".equals(lafName);
    }

    private boolean isMacLookAndFeel() {
        LookAndFeel laf = UIManager.getLookAndFeel();
        final String lafName = laf != null ? laf.getName() : null;
        return "Mac OS X".equals(lafName);
    }

    private void fixCheckBoxMenuItem(final JCheckBoxMenuItem item) {
        if (isWindowsLookAndFeel()) {
            if (item.isSelected()) {
                item.setIcon(UtplsqlResources.getIcon("CHECKMARK_ICON"));
            } else {
                item.setIcon(null);
            }
        }
    }
    
    private void comboBoxAction() {
        if (currentRun != null) {
            @SuppressWarnings("unchecked")
            final ComboBoxItem<String, String> comboBoxItem = (ComboBoxItem<String, String>) runComboBox
                    .getSelectedItem();
            if (currentRun.getReporterId() != null && !currentRun.getReporterId().equals(comboBoxItem.getKey())) {
                update(comboBoxItem.getKey());
                testDetailTabbedPane.setSelectedIndex(0);
            }
        }
    }

    private String getLinkedAndFormattedText(final String text) {
        if (text == null) {
            return "";
        }
        // Patterns (primarily Asserts, Errors, ServerOutput): 
        // at "OWNER.PACKAGE.PROCEDURE", line 42 
        // at "OWNER.PROCEDURE", line 42 
        // at "OWNER.PACKAGE", line 42
        // at package "OWNER.PACKAGE", line 42
        final Pattern p1 = Pattern.compile("\\s+(package\\s+)?(&quot;(\\S+?)\\.(\\S+?)(?:\\.(\\S+?))?&quot;,\\s+line\\s+([0-9]+))");
        String localText = HtmlUtils.htmlEscape(text);
        Matcher m = p1.matcher(localText);
        while (m.find()) {
            final String link = "<a href=\"" + (m.group(1) != null ? "PACKAGE" : "UNKNOWN") + "/" + m.group(3) + "/"
                    + m.group(4) + "/" + m.group(6) + "\">" + m.group(2) + "</a>";
            final int start = m.start(2);
            final int end = m.end(2);
            localText = localText.substring(0, start) + link + localText.substring(end);
            m = p1.matcher(localText);
        }
        // Patterns (primarily Warnings, without line reference, calculate when opening link):
        //   owner.package.procedure
        final Pattern p2 = Pattern.compile("^\\s{2}((\\S+?)\\.(\\S+?)\\.(\\S+?))$", Pattern.MULTILINE);
        m = p2.matcher(localText);
        while (m.find()) {
            final String link = "&nbsp;&nbsp;<a href=\"UNKNOWN/" + m.group(2).toUpperCase() + "/"
                    + m.group(3).toUpperCase() + "/1/" + m.group(4).toUpperCase() + "\">" + m.group(1) + "</a>";
            final int start = m.start(0);
            final int end = m.end(0);
            localText = localText.substring(0, start) + link + localText.substring(end);
            m = p2.matcher(localText);
        }
        // Patterns (Title for warning/info on suite level)
        // from suite a.junit_utplsql_test1_pkg:
        final Pattern p3 = Pattern.compile("^For suite ([^:]+):$", Pattern.MULTILINE);
        m = p3.matcher(localText);
        while (m.find()) {
            final String title = "<font size=\"4\"><b>For suite \"" + m.group(1) + "\"</b></font>";
            final int start = m.start(0);
            final int end = m.end(0);
            localText = localText.substring(0, start) + title + localText.substring(end);
            m = p3.matcher(localText);
        }
        StringBuilder sb = new StringBuilder();
        for (final String p : localText.split("\n")) {
            sb.append("<p>");
            sb.append(p);
            sb.append("</p>\n");
        }
        return sb.toString();
    }

    private JPanel makeLabelledCounterComponent(final JLabel label, final JComponent comp) {
        final JPanel groupPanel = new JPanel();
        groupPanel.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        // label
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.insets = new Insets(5, 10, 5, 0); // top, left, bottom, right
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        c.weighty = 0;
        groupPanel.add(label, c);
        // component
        c.gridx = 1;
        c.gridy = 0;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.insets = new Insets(5, 5, 5, 10); // top, left, bottom, right
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.weighty = 0;
        groupPanel.add(comp, c);
        final Dimension dim = new Dimension(134, 24);
        groupPanel.setMinimumSize(dim);
        groupPanel.setPreferredSize(dim);
        return groupPanel;
    }

    private void initializeGUI() {
        // Base panel containing all components 
        basePanel = new JPanel();
        basePanel.setLayout(new GridBagLayout());
        final GridBagConstraints c = new GridBagConstraints();
        
        // Toolbar
        final GradientToolbar toolbar = new GradientToolbar();
        toolbar.setFloatable(false);
        final EmptyBorder buttonBorder = new EmptyBorder(new Insets(2, 4, 2, 4)); // insets: top, left, bottom, right
        final ToolbarButton refreshButton = new ToolbarButton(UtplsqlResources.getIcon("REFRESH_ICON"));
        refreshButton.setToolTipText(UtplsqlResources.getString("RUNNER_REFRESH_TOOLTIP"));
        refreshButton.setBorder(buttonBorder);
        refreshButton.addActionListener(event -> {
            resetDerived();
            testDetailTabbedPane.setSelectedIndex(0);
            testOverviewTableModel.fireTableDataChanged();
        });
        toolbar.add(refreshButton);
        final ToolbarButton rerunButton = new ToolbarButton(UtplsqlResources.getIcon("RUN_ICON"));
        rerunButton.setToolTipText(UtplsqlResources.getString("RUNNER_RERUN_TOOLTIP"));
        rerunButton.setBorder(buttonBorder);
        rerunButton.addActionListener(event -> {
            final UtplsqlRunner runner = new UtplsqlRunner(currentRun.getPathList(), currentRun.getConnectionName());
            runner.runTestAsync();
        });
        toolbar.add(rerunButton);
        final ToolbarButton rerunWorksheetButton = new ToolbarButton(UtplsqlResources.getIcon("RUN_WORKSHEET_ICON"));
        rerunWorksheetButton.setToolTipText(UtplsqlResources.getString("RUNNER_RERUN_WORKSHEET_TOOLTIP"));
        rerunWorksheetButton.setBorder(buttonBorder);
        rerunWorksheetButton.addActionListener(event -> {
            final UtplsqlWorksheetRunner worksheet = new UtplsqlWorksheetRunner(currentRun.getPathList(),
                    currentRun.getConnectionName());
            worksheet.runTestAsync();
        });
        toolbar.add(rerunWorksheetButton);
        toolbar.add(Box.createHorizontalGlue());
        runComboBoxModel = new DefaultComboBoxModel<>();
        runComboBox = new JComboBox<>(runComboBoxModel);
        runComboBox.setEditable(false);
        final Dimension comboBoxDim = new Dimension(500, 50);
        runComboBox.setMaximumSize(comboBoxDim);
        runComboBox.addActionListener(event -> comboBoxAction());
        toolbar.add(runComboBox);
        final ToolbarButton clearButton = new ToolbarButton(UtplsqlResources.getIcon("CLEAR_ICON"));
        clearButton.setToolTipText(UtplsqlResources.getString("RUNNER_CLEAR_BUTTON"));
        clearButton.setBorder(buttonBorder);
        clearButton.addActionListener(event -> {
            final Run run = currentRun;
            runs.clear();
            currentRun = null;
            setModel(run);
            update(run.getReporterId());
        });
        toolbar.add(clearButton);
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        c.gridheight = 1;
        c.insets = new Insets(0, 0, 0, 0); // top, left, bottom, right
        c.anchor = GridBagConstraints.NORTH;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.weighty = 0;
        basePanel.add(toolbar, c);

        // Status line
        statusLabel = new JLabel();
        c.gridx = 0;
        c.gridy = 1;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.insets = new Insets(10, 10, 10, 0); // top, left, bottom, right
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.weighty = 0;
        basePanel.add(statusLabel, c);
        JLabel elapsedTimeLabel = new JLabel();
        elapsedTimeLabel.setPreferredSize(new Dimension(60, 0));
        c.gridx = 1;
        c.gridy = 1;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.insets = new Insets(10, 10, 10, 10); // top, left, bottom, right
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        c.weighty = 0;
        basePanel.add(elapsedTimeLabel, c);
        elapsedTimeTimer = new Timer(100, event -> {
            if (currentRun != null && currentRun.getStart() != null) {
                final SmartTime time = new SmartTime();
                time.setSmart(useSmartTimes);
                if (currentRun.getExecutionTime() != null) {
                    time.setSeconds(currentRun.getExecutionTime());
                    elapsedTimeTimer.stop();
                } else {
                    final Double now = Double.valueOf(System.currentTimeMillis());
                    time.setSeconds(Double.valueOf(now - currentRun.getStart()) / 1000);
                }
                elapsedTimeLabel.setText(time.toString() + (!useSmartTimes ? " s" : ""));
            } else {
                elapsedTimeLabel.setText(null);
            }
        });
        
        // Counters
        // - Test counter
        final JPanel counterPanel = new JPanel();
        counterPanel.setLayout(new WrapLayout(FlowLayout.LEFT, 0, 0));
        final JLabel testCounterLabel = new JLabel(UtplsqlResources.getString("RUNNER_TESTS_LABEL") + ":", JLabel.LEADING);
        testCounterValueLabel = new JLabel();
        counterPanel.add(makeLabelledCounterComponent(testCounterLabel, testCounterValueLabel));
        // - Failure counter
        final JLabel failureCounterLabel = new JLabel(UtplsqlResources.getString("RUNNER_FAILURES_LABEL") + ":",
                UtplsqlResources.getIcon("FAILURE_ICON"), JLabel.LEADING);
        failureCounterValueLabel = new JLabel();
        counterPanel.add(makeLabelledCounterComponent(failureCounterLabel, failureCounterValueLabel));
        // - Error counter
        final JLabel errorCounterLabel = new JLabel(UtplsqlResources.getString("RUNNER_ERRORS_LABEL") + ":",
                UtplsqlResources.getIcon("ERROR_ICON"), JLabel.LEADING);
        errorCounterValueLabel = new JLabel();
        counterPanel.add(makeLabelledCounterComponent(errorCounterLabel, errorCounterValueLabel));
        // - Disabled counter
        final JLabel disabledCounterLabel = new JLabel(UtplsqlResources.getString("RUNNER_DISABLED_LABEL") + ":",
                UtplsqlResources.getIcon("DISABLED_ICON"), JLabel.LEADING);
        disabledCounterValueLabel = new JLabel();
        counterPanel.add(makeLabelledCounterComponent(disabledCounterLabel, disabledCounterValueLabel));
        // - Warnings counter
        final JLabel warningsCounterLabel = new JLabel(UtplsqlResources.getString("RUNNER_WARNINGS_LABEL") + ":",
                UtplsqlResources.getIcon("WARNING_ICON"), JLabel.LEADING);
        warningsCounterValueLabel = new JLabel();
        counterPanel.add(makeLabelledCounterComponent(warningsCounterLabel, warningsCounterValueLabel));
        // - Info counter
        final JLabel infoCounterLabel = new JLabel(UtplsqlResources.getString("RUNNER_INFO_LABEL") + ":",
                UtplsqlResources.getIcon("INFO_ICON"), JLabel.LEADING);
        infoCounterValueLabel = new JLabel();
        counterPanel.add(makeLabelledCounterComponent(infoCounterLabel, infoCounterValueLabel));
        // - add everything to basePanel
        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 2;
        c.gridheight = 1;
        c.insets = new Insets(5, 0, 5, 0); // top, left, bottom, right
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.weighty = 0;
        basePanel.add(counterPanel, c);

        // Context menu for counters panel
        final JPopupMenu countersPopupMenu = new JPopupMenu();
        showDisabledCounterCheckBoxMenuItem = new JCheckBoxMenuItem(UtplsqlResources.getString("PREF_SHOW_DISABLED_COUNTER_LABEL").replace("?", ""), true);
        showDisabledCounterCheckBoxMenuItem.addActionListener(event -> {
            applyShowDisabledCounter();
            fixCheckBoxMenuItem(showDisabledCounterCheckBoxMenuItem);
        });
        countersPopupMenu.add(showDisabledCounterCheckBoxMenuItem);
        showWarningsCounterCheckBoxMenuItem = new JCheckBoxMenuItem(UtplsqlResources.getString("PREF_SHOW_WARNINGS_COUNTER_LABEL").replace("?", ""), true);
        showWarningsCounterCheckBoxMenuItem.addActionListener(event -> {
            applyShowWarningsCounter();
            fixCheckBoxMenuItem(showWarningsCounterCheckBoxMenuItem);
        });
        countersPopupMenu.add(showWarningsCounterCheckBoxMenuItem);
        showInfoCounterCheckBoxMenuItem = new JCheckBoxMenuItem( UtplsqlResources.getString("PREF_SHOW_INFO_COUNTER_LABEL").replace("?", ""), true);
        showInfoCounterCheckBoxMenuItem.addActionListener(event -> {
            applyShowInfoCounter();
            fixCheckBoxMenuItem(showInfoCounterCheckBoxMenuItem);
        });
        countersPopupMenu.add(showInfoCounterCheckBoxMenuItem);
        counterPanel.setComponentPopupMenu(countersPopupMenu);

        // Progress bar
        progressBar = new JProgressBar();
        final Dimension progressBarDim = new Dimension(10, 20);
        progressBar.setPreferredSize(progressBarDim);
        progressBar.setMinimumSize(progressBarDim);
        progressBar.setStringPainted(false);
        progressBar.setForeground(GREEN);
        progressBar.setUI(new BasicProgressBarUI());
        c.gridx = 0;
        c.gridy = 3;
        c.gridwidth = 2;
        c.gridheight = 1;
        c.insets = new Insets(10, 10, 10, 10); // top, left, bottom, right
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.weighty = 0;
        basePanel.add(progressBar, c);

        // Test overview
        testOverviewTableModel = new TestOverviewTableModel();
        testOverviewTable = new JTable(testOverviewTableModel);
        testOverviewTable.getTableHeader().setReorderingAllowed(false);
        testOverviewTable.setAutoCreateRowSorter(true);
        testOverviewTable.setRowHeight(OVERVIEW_TABLE_ROW_HEIGHT);
        testOverviewTable.getTableHeader().setPreferredSize(
                new Dimension(testOverviewTable.getTableHeader().getPreferredSize().width, OVERVIEW_TABLE_ROW_HEIGHT));
        testOverviewTable.getSelectionModel().addListSelectionListener(event -> {
            final int rowIndex = testOverviewTable.getSelectedRow();
            if (rowIndex != -1) {
                final int row = testOverviewTable.convertRowIndexToModel(rowIndex);
                final Test test = testOverviewTableModel.getTest(row);
                testOwnerTextField.setText(test.getOwnerName());
                testPackageTextField.setText(test.getObjectName());
                testProcedureTextField.setText(test.getProcedureName());
                testDescriptionTextArea.setText(test.getDescription() != null ? test.getDescription().trim() : null);
                testIdTextArea.setText(test.getId());
                testStartTextField.setText(StringTools.formatDateTime(test.getStartTime()));
                failuresTableModel.setModel(test.getFailedExpectations());
                failuresTableModel.fireTableDataChanged();
                testFailureMessageTextPane.setText(null);
                if (test.getFailedExpectations() != null && !test.getFailedExpectations().isEmpty()) {
                    failuresTable.setRowSelectionInterval(0, 0);
                }
                testErrorStackTextPane
                        .setText(getHtml(test.getErrorStack() != null ? test.getErrorStack().trim() : null));
                testWarningsTextPane.setText(getHtml(test.getWarnings() != null ? test.getWarnings().trim() : null));
                testServerOutputTextPane
                        .setText(getHtml(test.getServerOutput() != null ? test.getServerOutput().trim() : null));
                syncDetailTab();
                testOverviewRunMenuItem.setEnabled(true);
                testOverviewRunWorksheetMenuItem.setEnabled(true);
            }
        });
        testOverviewTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(final MouseEvent e) {
                if (e.getClickCount() == 2) {
                    if (failuresTable.getSelectedRowCount() == 1) {
                        openSelectedFailure();
                    } else {
                        openSelectedTest();
                    }
                }
            }
        });
        RepaintManager.currentManager(testOverviewTable).setDoubleBufferingEnabled(true);
        final TestTableHeaderRenderer testTableHeaderRenderer = new TestTableHeaderRenderer();
        final TableColumn overviewTableStatus = testOverviewTable.getColumnModel().getColumn(0);
        overviewTableStatus.setMinWidth(INDICATOR_WIDTH);
        overviewTableStatus.setPreferredWidth(INDICATOR_WIDTH);
        overviewTableStatus.setMaxWidth(INDICATOR_WIDTH);
        overviewTableStatus.setHeaderRenderer(testTableHeaderRenderer);
        final TableColumn overviewTableWarning = testOverviewTable.getColumnModel().getColumn(1);
        overviewTableWarning.setMinWidth(INDICATOR_WIDTH);
        overviewTableWarning.setPreferredWidth(INDICATOR_WIDTH);
        overviewTableWarning.setMaxWidth(INDICATOR_WIDTH);
        overviewTableWarning.setHeaderRenderer(testTableHeaderRenderer);
        final TableColumn overviewTableInfo = testOverviewTable.getColumnModel().getColumn(2);
        overviewTableInfo.setMinWidth(INDICATOR_WIDTH);
        overviewTableInfo.setPreferredWidth(INDICATOR_WIDTH);
        overviewTableInfo.setMaxWidth(INDICATOR_WIDTH);
        overviewTableInfo.setHeaderRenderer(testTableHeaderRenderer);
        final TableColumn overviewTableId = testOverviewTable.getColumnModel().getColumn(3);
        overviewTableId.setHeaderRenderer(testTableHeaderRenderer);
        final TableColumn overviewTableTime = testOverviewTable.getColumnModel().getColumn(4);
        overviewTableTime.setPreferredWidth(60);
        overviewTableTime.setMaxWidth(100);
        overviewTableTime.setHeaderRenderer(testTableHeaderRenderer);
        overviewTableTime.setCellRenderer(new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 7720067427609773267L;
            {
                setHorizontalAlignment(JLabel.RIGHT);
            }

            @Override
            public Component getTableCellRendererComponent(final JTable table, final Object value,
                    final boolean isSelected, final boolean hasFocus, final int row, final int col) {
                final SmartTime smartTime = new SmartTime(((Double) value), useSmartTimes);
                return super.getTableCellRendererComponent(table, smartTime.toString(), isSelected, hasFocus, row, col);
            }
        });
        final JScrollPane testOverviewScrollPane = new JScrollPane(testOverviewTable);
        
        // Context menu for test overview
        final JPopupMenu testOverviewPopupMenu = new JPopupMenu();
        testOverviewRunMenuItem = new JMenuItem(UtplsqlResources.getString("RUNNER_RUN_MENUITEM"), UtplsqlResources.getIcon("RUN_ICON"));
        testOverviewRunMenuItem.addActionListener(event -> {
            final UtplsqlRunner runner = new UtplsqlRunner(getPathListFromSelectedTests(),
                    currentRun.getConnectionName());
            runner.runTestAsync();
        });
        testOverviewPopupMenu.add(testOverviewRunMenuItem);
        testOverviewRunWorksheetMenuItem = new JMenuItem(UtplsqlResources.getString("RUNNER_RUN_WORKSHEET_MENUITEM"), UtplsqlResources.getIcon("RUN_WORKSHEET_ICON"));
        testOverviewRunWorksheetMenuItem.addActionListener(event -> {
            final UtplsqlWorksheetRunner worksheet = new UtplsqlWorksheetRunner(this.getPathListFromSelectedTests(),
                    currentRun.getConnectionName());
            worksheet.runTestAsync();
        });
        testOverviewPopupMenu.add(testOverviewRunWorksheetMenuItem);
        testOverviewPopupMenu.add(new JSeparator());
        showSuccessfulTestsCheckBoxMenuItem = new JCheckBoxMenuItem(UtplsqlResources.getString("PREF_SHOW_SUCCESSFUL_TESTS_LABEL").replace("?", ""), true);
        showSuccessfulTestsCheckBoxMenuItem.addActionListener(event -> {
            applyFilter(showSuccessfulTestsCheckBoxMenuItem.isSelected(),
                    showDisabledTestsCheckBoxMenuItem.isSelected());
            fixCheckBoxMenuItem(showSuccessfulTestsCheckBoxMenuItem);
        });
        testOverviewPopupMenu.add(showSuccessfulTestsCheckBoxMenuItem);
        showDisabledTestsCheckBoxMenuItem = new JCheckBoxMenuItem(UtplsqlResources.getString("PREF_SHOW_DISABLED_TESTS_LABEL").replace("?", ""), true);
        showDisabledTestsCheckBoxMenuItem.addActionListener(event -> {
            applyFilter(showSuccessfulTestsCheckBoxMenuItem.isSelected(),
                    showDisabledTestsCheckBoxMenuItem.isSelected());
            fixCheckBoxMenuItem(showDisabledTestsCheckBoxMenuItem);
        });
        testOverviewPopupMenu.add(showDisabledTestsCheckBoxMenuItem);
        testOverviewPopupMenu.add(new JSeparator());
        showTestDescriptionCheckBoxMenuItem = new JCheckBoxMenuItem(UtplsqlResources.getString("PREF_SHOW_TEST_DESCRIPTION_LABEL").replace("?", ""), true);
        showTestDescriptionCheckBoxMenuItem.addActionListener(event -> {
            applyShowTestDescription();
            fixCheckBoxMenuItem(showTestDescriptionCheckBoxMenuItem);
        });
        testOverviewPopupMenu.add(showTestDescriptionCheckBoxMenuItem);
        showWarningIndicatorCheckBoxMenuItem = new JCheckBoxMenuItem(UtplsqlResources.getString("PREF_SHOW_WARNING_INDICATOR_LABEL").replace("?", ""), true);
        showWarningIndicatorCheckBoxMenuItem.addActionListener(event -> {
            applyShowWarningIndicator(showWarningIndicatorCheckBoxMenuItem.isSelected());
            fixCheckBoxMenuItem(showWarningIndicatorCheckBoxMenuItem);
        });
        testOverviewPopupMenu.add(showWarningIndicatorCheckBoxMenuItem);
        showInfoIndicatorCheckBoxMenuItem = new JCheckBoxMenuItem(UtplsqlResources.getString("PREF_SHOW_INFO_INDICATOR_LABEL").replace("?", ""), true);
        showInfoIndicatorCheckBoxMenuItem.addActionListener(event -> {
            applyShowInfoIndicator(showInfoIndicatorCheckBoxMenuItem.isSelected());
            fixCheckBoxMenuItem(showInfoIndicatorCheckBoxMenuItem);
        });
        testOverviewPopupMenu.add(showInfoIndicatorCheckBoxMenuItem);
        syncDetailTabCheckBoxMenuItem = new JCheckBoxMenuItem(UtplsqlResources.getString("PREF_SYNC_DETAIL_TAB_LABEL").replace("?", ""), true);
        syncDetailTabCheckBoxMenuItem.addActionListener(event -> {
            syncDetailTab();
            fixCheckBoxMenuItem(syncDetailTabCheckBoxMenuItem);
        });
        testOverviewPopupMenu.add(syncDetailTabCheckBoxMenuItem);
        testOverviewTable.setComponentPopupMenu(testOverviewPopupMenu);
        testOverviewTable.getTableHeader().setComponentPopupMenu(testOverviewPopupMenu);

        // Test tabbed pane (Test Properties)
        final ScrollablePanel testInfoPanel = new ScrollablePanel();
        testInfoPanel.setLayout(new GridBagLayout());
        // - Owner
        final JLabel testOwnerLabel = new JLabel(UtplsqlResources.getString("RUNNER_OWNER_LABEL"));
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.insets = new Insets(10, 10, 0, 0); // top, left, bottom, right
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        c.weighty = 0;
        testInfoPanel.add(testOwnerLabel, c);
        testOwnerTextField = new RunnerTextField();
        testOwnerTextField.setEditable(false);
        c.gridx = 1;
        c.gridy = 0;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.insets = new Insets(10, 5, 0, 10); // top, left, bottom, right
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.weighty = 0;
        testInfoPanel.add(testOwnerTextField, c);
        // - Package
        final JLabel testPackageLabel = new JLabel(UtplsqlResources.getString("RUNNER_PACKAGE_LABEL"));
        c.gridx = 0;
        c.gridy = 1;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.insets = new Insets(5, 10, 0, 0); // top, left, bottom, right
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        c.weighty = 0;
        testInfoPanel.add(testPackageLabel, c);
        testPackageTextField = new RunnerTextField();
        testPackageTextField.setEditable(false);
        c.gridx = 1;
        c.gridy = 1;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.insets = new Insets(5, 5, 0, 10); // top, left, bottom, right
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.weighty = 0;
        testInfoPanel.add(testPackageTextField, c);
        // - Procedure
        final JLabel testProcedureLabel = new JLabel(UtplsqlResources.getString("RUNNER_PROCEDURE_LABEL"));
        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.insets = new Insets(5, 10, 0, 0); // top, left, bottom, right
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        c.weighty = 0;
        testInfoPanel.add(testProcedureLabel, c);
        testProcedureTextField = new RunnerTextField();
        testProcedureTextField.setEditable(false);
        c.gridx = 1;
        c.gridy = 2;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.insets = new Insets(5, 5, 0, 10); // top, left, bottom, right
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.weighty = 0;
        testInfoPanel.add(testProcedureTextField, c);
        // - Description
        final JLabel testDescriptionLabel = new JLabel(UtplsqlResources.getString("RUNNER_DESCRIPTION_LABEL"));
        testDescriptionLabel.setBorder(BorderFactory.createEmptyBorder(isMacLookAndFeel() ? 5 : 3, 0, 0, 0));
        c.gridx = 0;
        c.gridy = 3;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.insets = new Insets(5, 10, 0, 0); // top, left, bottom, right
        c.anchor = GridBagConstraints.NORTHWEST;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        c.weighty = 0;
        testInfoPanel.add(testDescriptionLabel, c);
        testDescriptionTextArea = new RunnerTextArea();
        testDescriptionTextArea.setEditable(false);
        testDescriptionTextArea.setEnabled(true);
        testDescriptionTextArea.setLineWrap(true);
        testDescriptionTextArea.setWrapStyleWord(true);
        c.gridx = 1;
        c.gridy = 3;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.insets = new Insets(5, 5, 0, 10); // top, left, bottom, right
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.weighty = 0;
        testInfoPanel.add(testDescriptionTextArea, c);
        // - Suitepath (id)
        final JLabel testIdLabel = new JLabel(UtplsqlResources.getString("RUNNER_TEST_ID_COLUMN"));
        testIdLabel.setBorder(BorderFactory.createEmptyBorder(isMacLookAndFeel() ? 5 : 3, 0, 0, 0));
        c.gridx = 0;
        c.gridy = 4;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.insets = new Insets(5, 10, 0, 0); // top, left, bottom, right
        c.anchor = GridBagConstraints.NORTHWEST;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        c.weighty = 0;
        testInfoPanel.add(testIdLabel, c);
        testIdTextArea = new RunnerTextArea();
        testIdTextArea.setEditable(false);
        testIdTextArea.setEnabled(true);
        testIdTextArea.setLineWrap(true);
        testIdTextArea.setWrapStyleWord(false);
        c.gridx = 1;
        c.gridy = 4;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.insets = new Insets(5, 5, 0, 10); // top, left, bottom, right
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.weighty = 0;
        testInfoPanel.add(testIdTextArea, c);
        // - Start
        final JLabel testStartLabel = new JLabel(UtplsqlResources.getString("RUNNER_START_LABEL"));
        c.gridx = 0;
        c.gridy = 5;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.insets = new Insets(5, 10, 10, 0); // top, left, bottom, right
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        c.weighty = 0;
        testInfoPanel.add(testStartLabel, c);
        testStartTextField = new RunnerTextField();
        testStartTextField.setEditable(false);
        c.gridx = 1;
        c.gridy = 5;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.insets = new Insets(5, 5, 10, 10); // top, left, bottom, right
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.weighty = 0;
        testInfoPanel.add(testStartTextField, c);
        c.gridx = 0;
        c.gridy = 6;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.insets = new Insets(0, 0, 0, 0); // top, left, bottom, right
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 0;
        c.weighty = 1;
        testInfoPanel.add(Box.createVerticalGlue(), c);
        final JScrollPane testPropertiesScrollPane = new JScrollPane(testInfoPanel);
        
        // Failures tabbed pane (failed expectations)
        // - failures table (number and description)
        failuresTableModel = new FailuresTableModel();
        failuresTable = new JTable(failuresTableModel);
        failuresTable.getTableHeader().setReorderingAllowed(false);
        failuresTable.getSelectionModel().addListSelectionListener(event -> {
            final int rowIndex = failuresTable.getSelectedRow();
            if (rowIndex != -1) {
                final int row = failuresTable.convertRowIndexToModel(rowIndex);
                final Expectation expectation = failuresTableModel.getExpectation(row);
                final String html = getHtml(expectation.getFailureText());
                testFailureMessageTextPane.setText(html);
            }
        });
        failuresTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(final MouseEvent e) {
                if (e.getClickCount() == 2 && failuresTable.getSelectedRowCount() == 1) {
                    openSelectedFailure();
                }
            }
        });
        final FailuresTableHeaderRenderer failuresTableHeaderRenderer = new FailuresTableHeaderRenderer();
        final TableColumn failuresTableNumber = failuresTable.getColumnModel().getColumn(0);
        failuresTableNumber.setHeaderRenderer(failuresTableHeaderRenderer);
        failuresTableNumber.setPreferredWidth(30);
        failuresTableNumber.setMaxWidth(30);
        final TableColumn failuresDescription = failuresTable.getColumnModel().getColumn(1);
        failuresDescription.setHeaderRenderer(failuresTableHeaderRenderer);
        final JScrollPane failuresTableScrollPane = new JScrollPane(failuresTable);
        // - failures details
        testFailureMessageTextPane = new RunnerTextPane();
        testFailureMessageTextPane.setEditable(false);
        testFailureMessageTextPane.setEnabled(true);
        testFailureMessageTextPane.setContentType("text/html");
        testFailureMessageTextPane.setMinimumSize(TEXTPANE_DIM);
        testFailureMessageTextPane.setPreferredSize(TEXTPANE_DIM);
        testFailureMessageTextPane.addHyperlinkListener(event -> {
            if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                final String link = event.getDescription();
                openLink(link);
            }
        });
        final JScrollPane testFailureMessageScrollPane = new JScrollPane(testFailureMessageTextPane);
        c.gridx = 1;
        c.gridy = 0;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.insets = new Insets(10, 5, 0, 10); // top, left, bottom, right
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1;
        c.weighty = 6;

        // - split pane
        final JSplitPane failuresSplitPane = new JSplitPane(SwingConstants.HORIZONTAL, failuresTableScrollPane,
                testFailureMessageScrollPane);
        failuresSplitPane.setResizeWeight(0.2);

        // Errors tabbed pane (Error Stack)
        final JPanel testErrorStackPanel = new JPanel();
        testErrorStackPanel.setLayout(new GridBagLayout());
        testErrorStackTextPane = new RunnerTextPane();
        testErrorStackTextPane.setEditable(false);
        testErrorStackTextPane.setEnabled(true);
        testErrorStackTextPane.setContentType("text/html");
        testErrorStackTextPane.setMinimumSize(TEXTPANE_DIM);
        testErrorStackTextPane.setPreferredSize(TEXTPANE_DIM);
        testErrorStackTextPane.addHyperlinkListener(event -> {
            if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                final String link = event.getDescription();
                openLink(link);
            }
        });
        final JScrollPane testErrorStackScrollPane = new JScrollPane(testErrorStackTextPane);
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.insets = new Insets(0, 0, 0, 0);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1;
        c.weighty = 1;
        testErrorStackPanel.add(testErrorStackScrollPane, c);

        // Warnings tabbed pane
        final JPanel testWarningsPanel = new JPanel();
        testWarningsPanel.setLayout(new GridBagLayout());
        testWarningsTextPane = new RunnerTextPane();
        testWarningsTextPane.setEditable(false);
        testWarningsTextPane.setEnabled(true);
        testWarningsTextPane.setContentType("text/html");
        testWarningsTextPane.setMinimumSize(TEXTPANE_DIM);
        testWarningsTextPane.setPreferredSize(TEXTPANE_DIM);
        testWarningsTextPane.addHyperlinkListener(event -> openLink(event.getDescription()));
        final JScrollPane testWarningsScrollPane = new JScrollPane(testWarningsTextPane);
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.insets = new Insets(0, 0, 0, 0); // top, left, bottom, right
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1;
        c.weighty = 1;
        testWarningsPanel.add(testWarningsScrollPane, c);

        // Info tabbed pane (Server Output)
        final JPanel testServerOutputPanel = new JPanel();
        testServerOutputPanel.setLayout(new GridBagLayout());
        testServerOutputTextPane = new RunnerTextPane();
        testServerOutputTextPane.setEditable(false);
        testServerOutputTextPane.setEnabled(true);
        testServerOutputTextPane.setContentType("text/html");
        testServerOutputTextPane.setMinimumSize(TEXTPANE_DIM);
        testServerOutputTextPane.setPreferredSize(TEXTPANE_DIM);
        testServerOutputTextPane.addHyperlinkListener(event -> openLink(event.getDescription()));
        final JScrollPane testServerOutputScrollPane = new JScrollPane(testServerOutputTextPane);
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.insets = new Insets(0, 0, 0, 0); // top, left, bottom, right
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1;
        c.weighty = 1;
        testServerOutputPanel.add(testServerOutputScrollPane, c);

        // split pane with all tabs
        testDetailTabbedPane = new JTabbedPane();
        testDetailTabbedPane.add(UtplsqlResources.getString("RUNNER_TEST_TAB_LABEL"), testPropertiesScrollPane);
        testDetailTabbedPane.add(UtplsqlResources.getString("RUNNER_FAILURES_TAB_LABEL"), failuresSplitPane);
        testDetailTabbedPane.add(UtplsqlResources.getString("RUNNER_ERRORS_TAB_LABEL"), testErrorStackPanel);
        testDetailTabbedPane.add(UtplsqlResources.getString("RUNNER_WARNINGS_TAB_LABEL"), testWarningsPanel);
        testDetailTabbedPane.add(UtplsqlResources.getString("RUNNER_INFO_TAB_LABEL"), testServerOutputPanel);
        final JSplitPane horizontalSplitPane = new JSplitPane(SwingConstants.HORIZONTAL, testOverviewScrollPane,
                testDetailTabbedPane);
        horizontalSplitPane.setResizeWeight(0.5);
        c.gridx = 0;
        c.gridy = 4;
        c.gridwidth = 2;
        c.gridheight = 1;
        c.insets = new Insets(10, 10, 10, 10); // top, left, bottom, right
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1;
        c.weighty = 1;
        basePanel.add(horizontalSplitPane, c);
        if (isMacLookAndFeel()) {
            final CompoundBorder border = BorderFactory.createCompoundBorder(
                    BorderFactory.createEmptyBorder(3, 3, 3, 3),
                    BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(219, 219, 219)),
                            BorderFactory.createEmptyBorder(1, 1, 1, 1)));
            testDescriptionTextArea.setBorder(border);
            testIdTextArea.setBorder(border);
        } else {
            final Border referenceBorder = testOwnerTextField.getBorder();
            testDescriptionTextArea.setBorder(referenceBorder);
            testIdTextArea.setBorder(referenceBorder);
        }
    }
}
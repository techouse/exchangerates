package com.techouse.exchangerates;

import javax.swing.*;
import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.InternationalFormatter;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.SortedSet;

public class ExchangeRateGUI
{
    private static final String JAVA_VERSION = System.getProperty("java.runtime.version");
    private static final String AUTHOR = "Klemen Tušar";
    private static final String EMAIL = "techouse@gmail.com";
    private static final String VERSION = "1.1.4";
    private static final String TITLE = "ECB Exchange Rates";
    private static boolean preparingDatabase = false;
    private static ExchangeRateGUI instance;
    private DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy 'at' HH:mm:ss");
    private JList<String> currenciesList;
    private JScrollPane currenciesListScroller;
    private JTable ratesTable;
    private JScrollPane tableScroller;
    private JPanel panel;
    private JLabel currentCurrency;
    private JFormattedTextField currentCurrencyValue;
    private JLabel databaseStatus;
    private JMenuBar menuBar;
    private JMenuItem showChart;
    private boolean sortCurrencies = true;
    private boolean sortExchangeRates = false;
    private JFrame frame;

    private ExchangeRateGUI()
    {
        ExchangeRateGUI.instance = this;

        SortedSet<String> currencies = ReferenceRates.getCurrencies();
        String[] currenciesArray = new String[currencies.size()];
        currenciesList.setListData(currencies.toArray(currenciesArray));
        currenciesList.setSelectedIndex(currencies.headSet(ReferenceRates.REFERENCE_CURRENCY).size());

        ratesTable.setModel(new ExchangeRatesTable());
        tableScroller.setViewportView(ratesTable);

        currenciesListScroller.setViewportView(currenciesList);
        currenciesList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selectedCurrency = currenciesList.getSelectedValue();

                ExchangeRatesTable currentData = (ExchangeRatesTable) ratesTable.getModel();

                ratesTable.setModel(new ExchangeRatesTable(
                    selectedCurrency,
                    false,
                    currentData.sortColumn,
                    currentData.sortDescending,
                    currentData.multiplyFactor
                ));

                currentCurrency.setText(selectedCurrency);
            }
        });

        ratesTable.getTableHeader().addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                int col = ratesTable.columnAtPoint(e.getPoint());

                ExchangeRatesTable currentData = (ExchangeRatesTable) ratesTable.getModel();

                switch (col) {
                    case 0:
                        ratesTable.setModel(new ExchangeRatesTable(
                            currenciesList.getSelectedValue(),
                            false,
                            0,
                            sortCurrencies,
                            currentData.multiplyFactor
                        ));
                        sortCurrencies = !sortCurrencies;
                        sortExchangeRates = false;
                        break;
                    case 1:
                        ratesTable.setModel(new ExchangeRatesTable(
                            currenciesList.getSelectedValue(),
                            false,
                            1,
                            sortExchangeRates,
                            currentData.multiplyFactor
                        ));
                        sortCurrencies = false;
                        sortExchangeRates = !sortExchangeRates;
                        break;
                }
            }
        });

        JPopupMenu tablePopupMenu = new JPopupMenu();

        JMenuItem copyItem = new JMenuItem("Copy", KeyEvent.VK_C);
        copyItem.setIcon(new ImageIcon(getClass().getResource("assets/images/copy.png")));
        copyItem.addActionListener(e -> {
            StringBuilder data = new StringBuilder();
            data.append(currentCurrencyValue.getValue().toString());
            data.append(" ");
            data.append(ReferenceRates.REFERENCE_CURRENCY);
            data.append(" = ");
            data.append(ratesTable.getValueAt(ratesTable.getSelectedRow(), 1).toString());
            data.append(" ");
            data.append(ratesTable.getValueAt(ratesTable.getSelectedRow(), 0).toString());

            StringSelection stringSelection = new StringSelection(data.toString());
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(stringSelection, null);
        });
        tablePopupMenu.add(copyItem);

        showChart = new JMenuItem("History Chart", KeyEvent.VK_H);
        showChart.setIcon(new ImageIcon(getClass().getResource("assets/images/chart_icon.png")));
        showChart.addActionListener(e -> new ChartWorker().execute());
        tablePopupMenu.add(showChart);

        ratesTable.setComponentPopupMenu(tablePopupMenu);

        ratesTable.addMouseListener(new TableMouseListener(ratesTable));

        buildMenuBar();

        buildSimpleCurrencyCalculator();
    }

    public static void main(String[] args)
    {
        if (!JAVA_VERSION.startsWith("1.8.")) {
            System.out.println("Java 8 required to run this app!\nPlease update your Java JRE to 8 or above.");
            System.exit(1);
        }

        StringBuilder aboutText = new StringBuilder(TITLE);
        aboutText.append(' ');
        aboutText.append('v');
        aboutText.append(VERSION);
        aboutText.append("\nWritten by ");
        aboutText.append(AUTHOR);
        aboutText.append(" <");
        aboutText.append(EMAIL);
        aboutText.append(">");
        aboutText.append("\nRunning ");
        aboutText.append(System.getProperty("java.runtime.name"));
        aboutText.append(" ");
        aboutText.append(JAVA_VERSION);
        aboutText.append(" on ");
        aboutText.append(System.getProperty("os.name"));
        aboutText.append(" ");
        aboutText.append(System.getProperty("os.version"));

        System.out.println(aboutText.toString());

        /*
         * Set the UI look and feel to the system default
         */
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
            e.printStackTrace();
        }

        /*
         * Schedule a job for the event-dispatching thread:
         * creating and showing this application's GUI.
         */
        javax.swing.SwingUtilities.invokeLater(() -> new ExchangeRateGUI().createAndShowGUI());
    }

    public static boolean isPreparingDatabase()
    {
        return preparingDatabase;
    }

    static void setPreparingDatabase(boolean preparingDatabase)
    {
        ExchangeRateGUI.preparingDatabase = preparingDatabase;

        if (preparingDatabase) {
            instance.databaseStatus.setText("Preparing database. Please wait ...");
            instance.showChart.setEnabled(false);
        } else {
            instance.databaseStatus.setText("Database ready.");
            instance.showChart.setEnabled(true);

            new Timer(5000, evt -> instance.databaseStatus.setText("")).start();
        }
    }

    private void buildSimpleCurrencyCalculator()
    {
        DecimalFormat decimalFormat = (DecimalFormat) NumberFormat.getNumberInstance(Locale.US);
        decimalFormat.setMinimumFractionDigits(2);
        decimalFormat.setMaximumFractionDigits(5);
        InternationalFormatter numberFormatFormatter = new InternationalFormatter(decimalFormat);
        numberFormatFormatter.setMinimum(0d);
        numberFormatFormatter.setValueClass(Double.class);
        numberFormatFormatter.setCommitsOnValidEdit(true);
        DefaultFormatterFactory numberFormatFormatterFactory = new DefaultFormatterFactory(numberFormatFormatter);

        currentCurrencyValue.setFormatterFactory(numberFormatFormatterFactory);
        currentCurrencyValue.setValue(1d);
        currentCurrencyValue.setColumns(20);
        currentCurrencyValue.addPropertyChangeListener("value", evt -> {
            Double value = evt.getNewValue() != null ? (Double) evt.getNewValue() : 1d;

            ExchangeRatesTable currentData = (ExchangeRatesTable) ratesTable.getModel();

            ratesTable.setModel(new ExchangeRatesTable(
                currentData.currency,
                currentData.refresh,
                currentData.sortColumn,
                currentData.sortDescending,
                value
            ));
        });

        currentCurrency.setLabelFor(currentCurrencyValue);
        currentCurrency.setText(ReferenceRates.REFERENCE_CURRENCY);

    }

    private void refreshTableData()
    {
        new DatabaseWorker().execute();

        ExchangeRatesTable currentData = (ExchangeRatesTable) ratesTable.getModel();

        ratesTable.setModel(new ExchangeRatesTable(
            currentData.currency,
            true,
            currentData.sortColumn,
            currentData.sortDescending,
            currentData.multiplyFactor
        ));
        StringBuilder message = new StringBuilder("Data updated on ");
        message.append(LocalDateTime.now().format(dateFormat));
        JOptionPane.showMessageDialog(null, message.toString());
    }

    private void buildMenuBar()
    {
        //Create the menu bar
        menuBar = new JMenuBar();

        JMenu dataMenu = new JMenu("Data");
        dataMenu.setMnemonic(KeyEvent.VK_D);

        JMenuItem refreshExchangeRates = new JMenuItem("Refresh Exchange Rates", KeyEvent.VK_R);
        refreshExchangeRates.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F5, ActionEvent.CTRL_MASK));
        refreshExchangeRates.addActionListener(e -> refreshTableData());
        dataMenu.add(refreshExchangeRates);

        menuBar.add(dataMenu);

        // Build Help menu in the menu bar.
        JMenu helpMenu = new JMenu("Help");
        helpMenu.setMnemonic(KeyEvent.VK_H);

        JMenuItem about = new JMenuItem("About", KeyEvent.VK_A);
        about.addActionListener(e -> {
            StringBuilder aboutText = new StringBuilder(TITLE);
            aboutText.append(" v");
            aboutText.append(VERSION);
            aboutText.append("\n\nWritten by ");
            aboutText.append(AUTHOR);
            aboutText.append(" <");
            aboutText.append(EMAIL);
            aboutText.append(">");
            aboutText.append("\n\nRunning ");
            aboutText.append(System.getProperty("java.runtime.name"));
            aboutText.append(" ");
            aboutText.append(JAVA_VERSION);
            aboutText.append("\non ");
            aboutText.append(System.getProperty("os.name"));
            aboutText.append(" ");
            aboutText.append(System.getProperty("os.version"));

            JOptionPane.showMessageDialog(null, aboutText.toString());
        });
        helpMenu.add(about);

        JMenuItem dataSource = new JMenuItem("Data Source", KeyEvent.VK_S);
        dataSource.addActionListener(e -> JOptionPane.showMessageDialog(
            null,
            null,
            "Euro foreign exchange reference rates",
            JOptionPane.INFORMATION_MESSAGE,
            new ImageIcon(getClass().getResource("assets/images/ecb.png"))
        ));
        helpMenu.add(dataSource);

        menuBar.add(helpMenu);
    }

    private void createAndShowGUI()
    {
        new DatabaseWorker().execute();

        frame = new JFrame(TITLE);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setContentPane(panel);
        frame.setJMenuBar(menuBar);
        frame.pack();
        frame.setVisible(true);
    }

    private class ChartWorker extends SwingWorker
    {
        @Override
        protected Integer doInBackground() throws Exception
        {
            if (!preparingDatabase) {
                frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                JDialog chart = new ExchangeRatesChart(
                    ratesTable.getValueAt(ratesTable.getSelectedRow(), 0).toString(),
                    currenciesList.getSelectedValue()
                );
                chart.setLocationRelativeTo(null);
                chart.setVisible(true);
            }

            return 1;
        }

        protected void done()
        {
            frame.setCursor(Cursor.getDefaultCursor());
        }
    }

    private class TableMouseListener extends MouseAdapter
    {

        private JTable table;

        public TableMouseListener(JTable table)
        {
            this.table = table;
        }

        @Override
        public void mouseReleased(MouseEvent event)
        {
            // selects the row at which point the mouse is clicked
            Point point = event.getPoint();
            int currentRow = table.rowAtPoint(point);
            table.setRowSelectionInterval(currentRow, currentRow);
        }

        @Override
        public void mousePressed(MouseEvent event)
        {
            // selects the row at which point the mouse is clicked
            Point point = event.getPoint();
            int currentRow = table.rowAtPoint(point);
            table.setRowSelectionInterval(currentRow, currentRow);
        }
    }

    private class DatabaseWorker extends SwingWorker
    {
        @Override
        protected Integer doInBackground() throws Exception
        {
            HistoricReferenceRates.prepareDatabase();
            return 1;
        }
    }
}

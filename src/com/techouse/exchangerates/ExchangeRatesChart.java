package com.techouse.exchangerates;

import org.jfree.chart.*;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.labels.StandardXYToolTipGenerator;
import org.jfree.chart.panel.CrosshairOverlay;
import org.jfree.chart.plot.Crosshair;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.general.DatasetUtilities;
import org.jfree.data.time.Day;
import org.jfree.data.time.MovingAverage;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.XYDataset;
import org.jfree.ui.RectangleEdge;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

class ExchangeRatesChart extends JDialog
{
    private static final String TITLE = "Exchange Rates Chart";
    private static final String X_AXIS_LABEL = "Date";
    private static final SimpleDateFormat dateFormatter = new SimpleDateFormat("d-MMM-yyyy");
    private static final DecimalFormat decimalFormatter = (DecimalFormat) NumberFormat.getNumberInstance(Locale.US);
    private StringBuilder title;
    private JPanel chartPanel;

    ExchangeRatesChart(String currency)
    {
        this(currency, ReferenceRates.REFERENCE_CURRENCY);
    }

    ExchangeRatesChart(String currency, String baseCurrency)
    {
        this.chartPanel = new ExchangeRatesPanel(currency, baseCurrency);
        add(this.chartPanel, BorderLayout.CENTER);

        setSize(1024, 600);
        setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);

        this.title = new StringBuilder(TITLE);
        this.title.append(" for ");
        this.title.append(currency);
        this.title.append("/");
        this.title.append(baseCurrency);
        setTitle(this.title.toString());
    }

    private class ExchangeRatesPanel extends JPanel implements ChartMouseListener, ActionListener
    {
        private JFreeChart chart;
        private XYLineAndShapeRenderer renderer;
        private XYPlot plot;
        private ChartPanel chartPanel;
        private JPanel controlPanel;
        private Crosshair xCrosshair, yCrosshair;
        private String currency, baseCurrency;

        ExchangeRatesPanel(String currency, String baseCurrency)
        {
            super(new BorderLayout());

            this.currency = currency;
            this.baseCurrency = baseCurrency;
            this.chart = createChart(this.currency, this.baseCurrency);

            this.plot = this.chart.getXYPlot();

            this.renderer = (XYLineAndShapeRenderer) this.plot.getRenderer();
            this.renderer.setAutoPopulateSeriesStroke(false);
            this.renderer.setBaseStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            this.renderer.setDrawSeriesLineAsPath(true);
            this.renderer.setBaseToolTipGenerator(new StandardXYToolTipGenerator(
                StandardXYToolTipGenerator.DEFAULT_TOOL_TIP_FORMAT,
                dateFormatter, decimalFormatter
            ));
            GeneralPath zigzag = new GeneralPath();
            zigzag.moveTo(-6.0f, 0.0f);
            zigzag.lineTo(-3.0f, 6.0f);
            zigzag.lineTo(3.0f, -6.0f);
            zigzag.lineTo(6.0f, 0.0f);
            this.renderer.setLegendLine(zigzag);

            ChartUtilities.applyCurrentTheme(this.chart);

            this.chartPanel = new ChartPanel(this.chart);
            this.chartPanel.addChartMouseListener(this);
            CrosshairOverlay crosshairOverlay = new CrosshairOverlay();
            this.xCrosshair = new Crosshair(
                Double.NaN,
                Color.GRAY,
                new BasicStroke(0f)
            );
            this.xCrosshair.setLabelVisible(true);
            this.yCrosshair = new Crosshair(
                Double.NaN,
                Color.GRAY,
                new BasicStroke(0f)
            );
            this.yCrosshair.setLabelVisible(true);
            crosshairOverlay.addDomainCrosshair(xCrosshair);
            crosshairOverlay.addRangeCrosshair(yCrosshair);
            this.chartPanel.addOverlay(crosshairOverlay);
            this.chartPanel.setMouseWheelEnabled(true);

            this.plot.setDomainCrosshairVisible(true);
            this.plot.setRangeCrosshairVisible(true);
            this.plot.setDomainPannable(true);
            this.plot.setRangePannable(true);

            add(this.chartPanel);

            StringBuilder timeSeriesTitle = new StringBuilder(currency.toUpperCase());
            timeSeriesTitle.append("/");
            timeSeriesTitle.append(this.baseCurrency.toUpperCase());

            this.controlPanel = new JPanel();

            JCheckBox box1 = new JCheckBox(timeSeriesTitle.toString());
            box1.setActionCommand("S1");
            box1.addActionListener(this);
            box1.setSelected(true);
            this.controlPanel.add(box1);

            JCheckBox box2 = new JCheckBox("30 DAY AVG");
            box2.setActionCommand("S2");
            box2.addActionListener(this);
            box2.setSelected(true);
            this.controlPanel.add(box2);

            JButton invertButton = new JButton("INVERT", new ImageIcon(getClass().getResource("assets/images/invert.png")));
            invertButton.setActionCommand("INVERT");
            invertButton.addActionListener(this);
            this.controlPanel.add(invertButton);

            add(this.controlPanel, BorderLayout.SOUTH);
        }

        private JFreeChart createChart()
        {
            return createChart(this.currency, this.baseCurrency);
        }

        private JFreeChart createChart(String currency, String baseCurrency)
        {
            return ChartFactory.createTimeSeriesChart(
                null,
                X_AXIS_LABEL,
                currency,
                createDataSet(currency, baseCurrency),
                true,
                true,
                false
            );
        }

        private XYDataset createDataSet()
        {
            return createDataSet(this.currency, this.baseCurrency);
        }

        private XYDataset createDataSet(String currency, String baseCurrency)
        {
            Map<LocalDate, Double> rates = HistoricReferenceRates.getCurrencyHistory(currency, baseCurrency);
            TimeSeriesCollection dataSet = new TimeSeriesCollection();
            StringBuilder timeSeriesTitle = new StringBuilder(currency.toUpperCase());
            timeSeriesTitle.append("/");
            timeSeriesTitle.append(baseCurrency.toUpperCase());

            TimeSeries ratesSeries = new TimeSeries(timeSeriesTitle.toString());
            for (LocalDate date : rates.keySet()) {
                ratesSeries.add(new Day(DateUtils.asDate(date)), rates.get(date));
            }
            dataSet.addSeries(ratesSeries);
            dataSet.addSeries(MovingAverage.createMovingAverage(ratesSeries, "30 DAY AVG", 30, 30));

            return dataSet;
        }

        @Override
        public void chartMouseClicked(ChartMouseEvent event)
        {
            // ignore
        }

        @Override
        public void chartMouseMoved(ChartMouseEvent event)
        {
            Rectangle2D dataArea = this.chartPanel.getScreenDataArea();
            DateAxis xAxis = (DateAxis) this.plot.getDomainAxis();
            double x = xAxis.java2DToValue(
                event.getTrigger().getX(),
                dataArea,
                RectangleEdge.BOTTOM
            );
            // make the crosshairs disappear if the mouse is out of range
            if (!xAxis.getRange().contains(x)) {
                x = Double.NaN;
            }
            double y = DatasetUtilities.findYValue(this.plot.getDataset(), 0, x);
            this.xCrosshair.setValue(x);
            this.yCrosshair.setValue(y);

            this.yCrosshair.setLabelGenerator(arg0 -> {
                StringBuilder label = new StringBuilder(decimalFormatter.format(arg0.getValue()));
                label.append(" ");
                label.append(this.currency.toUpperCase());
                return label.toString();
            });
            this.xCrosshair.setLabelGenerator(arg0 -> dateFormatter.format(new Date((long) arg0.getValue())));
        }

        @Override
        public void actionPerformed(ActionEvent e)
        {
            int series = -1;
            if (e.getActionCommand().equals("S1")) {
                series = 0;
            } else if (e.getActionCommand().equals("S2")) {
                series = 1;
            } else if (e.getActionCommand().equals("INVERT")) {
                new InvertChart().execute();
            }
            if (series >= 0) {
                boolean visible = this.renderer.getItemVisible(series, 0);
                this.renderer.setSeriesVisible(series, !visible);
                if (series == 0) {
                    this.xCrosshair.setVisible(!visible);
                    this.yCrosshair.setVisible(!visible);
                }
            }
        }

        private class InvertChart extends SwingWorker
        {
            @Override
            protected Integer doInBackground() throws Exception
            {
                ExchangeRatesChart.this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

                String invertedCurrency = baseCurrency;
                String invertedBaseCurrency = currency;
                currency = invertedCurrency;
                baseCurrency = invertedBaseCurrency;
                plot.setDataset(0, createDataSet());
                plot.getRangeAxis().setLabel(currency);

                StringBuilder title = new StringBuilder(TITLE);
                title.append(" for ");
                title.append(currency);
                title.append("/");
                title.append(baseCurrency);

                ExchangeRatesChart.this.setTitle(title.toString());

                return 1;
            }

            protected void done()
            {
                ExchangeRatesChart.this.setCursor(Cursor.getDefaultCursor());
            }
        }
    }
}

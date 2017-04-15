package com.techouse.exchangerates;

import javax.swing.table.AbstractTableModel;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.stream.Collectors;

class ExchangeRatesTable extends AbstractTableModel
{
    String currency;
    boolean refresh;
    int sortColumn;
    boolean sortDescending;
    Double multiplyFactor;
    private DecimalFormat decimalFormat = (DecimalFormat) NumberFormat.getNumberInstance(Locale.US);
    private Map<String, Double> exchangeRates;
    private Map<String, Double> exchangeRatesDisplay;
    private String[] keys;

    ExchangeRatesTable()
    {
        this(ReferenceRates.REFERENCE_CURRENCY, false, -1, false, 1d);
    }

    ExchangeRatesTable(String currency)
    {
        this(currency, false, -1, false, 1d);
    }

    ExchangeRatesTable(boolean refresh)
    {
        this(ReferenceRates.REFERENCE_CURRENCY, refresh, -1, false, 1d);
    }

    ExchangeRatesTable(String currency, boolean refresh)
    {
        this(currency, refresh, -1, false, 1d);
    }

    ExchangeRatesTable(String currency, boolean refresh, int sortColumn)
    {
        this(currency, refresh, sortColumn, false, 1d);
    }

    ExchangeRatesTable(String currency, boolean refresh, int sortColumn, boolean sortDescending)
    {
        this(currency, refresh, sortColumn, sortDescending, 1d);
    }

    ExchangeRatesTable(String currency, boolean refresh, int sortColumn, boolean sortDescending, Double multiplyFactor)
    {
        this.currency = currency;
        this.refresh = refresh;
        this.sortColumn = sortColumn;
        this.sortDescending = sortDescending;
        this.multiplyFactor = multiplyFactor;

        decimalFormat.setMinimumFractionDigits(2);
        decimalFormat.setMaximumFractionDigits(5);
        decimalFormat.setRoundingMode(RoundingMode.HALF_UP);

        this.exchangeRates = multiplyFactor == null || multiplyFactor == 1d
            ? GetExchangeRate.calculateRates(currency, refresh)
            : ExchangeRatesTable.multiplyBy(GetExchangeRate.calculateRates(currency, refresh), multiplyFactor);
        this.exchangeRatesDisplay = this.exchangeRates
            .entrySet()
            .stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue
            ));
        this.exchangeRatesDisplay.remove(currency);

        if (sortColumn == 1) {
            this.exchangeRatesDisplay = sortDescending ? DataUtils.sortByValueDesc(this.exchangeRatesDisplay) : DataUtils.sortByValueAsc(this.exchangeRatesDisplay);
        }

        this.keys = this.exchangeRatesDisplay.keySet().toArray(new String[this.exchangeRatesDisplay.size()]);

        if (sortColumn != 1) {
            if (sortDescending) {
                Arrays.sort(this.keys, Collections.reverseOrder());
            } else {
                Arrays.sort(this.keys);
            }
        }
    }

    private static Map<String, Double> multiplyBy(Map<String, Double> exchangeRates, Double factor)
    {
        Map<String, Double> newExchangeRates = new HashMap<>();
        BigDecimal value;
        BigDecimal multiply = new BigDecimal(factor.toString());

        for (String currency : exchangeRates.keySet()) {
            value = new BigDecimal(exchangeRates.get(currency).toString()).multiply(multiply);
            newExchangeRates.put(currency, value.setScale(4, RoundingMode.CEILING).doubleValue());
        }

        return newExchangeRates;
    }

    @Override
    public String getColumnName(int column)
    {
        return column == 0 ? "Currency" : "Exchange rate";
    }

    @Override
    public int getRowCount()
    {
        return exchangeRatesDisplay.size();
    }

    @Override
    public int getColumnCount()
    {
        return 2;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex)
    {
        return columnIndex == 0 ? keys[rowIndex] : decimalFormat.format(exchangeRatesDisplay.get(keys[rowIndex]));
    }
}

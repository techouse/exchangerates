package com.techouse.exchangerates;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

class GetExchangeRate
{
    private static final String ECB_URL = "https://www.ecb.europa.eu/stats/exchange/eurofxref/html/index.en.html";

    public static Map<String, Double> calculateRates()
    {
        return calculateRates(ReferenceRates.REFERENCE_CURRENCY, false);
    }

    public static Map<String, Double> calculateRates(String currency)
    {
        return calculateRates(currency, false);
    }

    public static Map<String, Double> calculateRates(boolean refresh)
    {
        return calculateRates(ReferenceRates.REFERENCE_CURRENCY, refresh);
    }

    static Map<String, Double> calculateRates(String currency, boolean refresh)
    {
        Map<String, Double> calculatedRates = new HashMap<>();
        Map<String, Double> referenceRates = ReferenceRates.getRates();

        DecimalFormat decimalFormat0 = new DecimalFormat("#.#####", new DecimalFormatSymbols(Locale.US));
        decimalFormat0.setRoundingMode(RoundingMode.HALF_UP);

        DecimalFormat decimalFormat1 = new DecimalFormat("#.####", new DecimalFormatSymbols(Locale.US));
        decimalFormat1.setRoundingMode(RoundingMode.HALF_UP);

        DecimalFormat decimalFormat100 = new DecimalFormat("#.##", new DecimalFormatSymbols(Locale.US));
        decimalFormat100.setRoundingMode(RoundingMode.HALF_UP);

        if (!currency.equals(ReferenceRates.REFERENCE_CURRENCY)) {
            Double currencyRate = referenceRates.get(currency);

            for (Map.Entry<String, Double> referenceRate : referenceRates.entrySet()) {
                BigDecimal value = new BigDecimal(Double.toString(referenceRate.getValue()));
                value = value.divide(new BigDecimal(currencyRate.toString()), 4, BigDecimal.ROUND_HALF_UP);
                value = value.setScale(4, BigDecimal.ROUND_HALF_UP);
                Double calculatedRate = value.doubleValue();

                if (calculatedRate < 1) {
                    calculatedRate = Double.parseDouble(decimalFormat0.format(calculatedRate));
                } else if (calculatedRate < 100 && calculatedRate >= 1) {
                    calculatedRate = Double.parseDouble(decimalFormat1.format(calculatedRate));
                } else {
                    calculatedRate = Double.parseDouble(decimalFormat100.format(calculatedRate));
                }

                calculatedRates.put(referenceRate.getKey(), calculatedRate);
            }

            return calculatedRates;
        } else {
            return referenceRates;
        }
    }
}

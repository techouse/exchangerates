package com.techouse.exchangerates;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

class ReferenceRates
{
    static final String REFERENCE_CURRENCY = "EUR";
    static final String ECB_DAILY_XML_URL = "https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml";
    private static final String NODE_NAME = "Cube";
    private static final String DATE = "time";
    private static final String CURRENCY = "currency";
    private static final String RATE = "rate";

    private static Map<String, Object> data;
    private static SortedSet<String> currencies;

    static Map<String, Double> getRates()
    {
        return getRates(false);
    }

    static Map<String, Double> getRates(boolean refresh)
    {
        if (refresh || data == null) {
            data = ReferenceRates.getReferenceRates();
        }
        return (Map<String, Double>) data.get("rates");
    }

    static SortedSet<String> getCurrencies()
    {
        if (currencies == null) {
            currencies = new TreeSet<>(getRates().keySet());
        }
        return currencies;
    }

    static LocalDate getDate()
    {
        if (data == null) {
            data = ReferenceRates.getReferenceRates();
        }
        return (LocalDate) data.get("date");
    }

    static Map<String, Object> getReferenceRates()
    {
        Map<String, Object> data = new HashMap<>();
        Map<String, Double> rates = new HashMap<String, Double>()
        {{
            put(REFERENCE_CURRENCY, 1d);
        }};
        NodeList nodeList = GetDataFromUrl
            .getDocument(ECB_DAILY_XML_URL)
            .getElementsByTagName(NODE_NAME);

        DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                if (element.hasAttribute(DATE)) {
                    data.put("date", LocalDate.parse(element.getAttribute(DATE), dateFormat));
                } else if (element.hasAttribute(CURRENCY) && element.hasAttribute(RATE)) {
                    rates.put(
                        element.getAttribute(CURRENCY),
                        Double.parseDouble(element.getAttribute(RATE))
                    );
                }
            }
        }

        data.put("rates", rates);

        return data;
    }
}

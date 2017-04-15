package com.techouse.exchangerates;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;

class GetDataFromUrl
{
    public static String getStringData(String url)
    {
        StringBuilder data = new StringBuilder();

        try {
            URL dataUrl = new URL(url);
            try (
                BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(
                        dataUrl.openStream()
                    )
                )
            ) {
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    data.append(line);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return data.toString();
    }

    static Document getDocument(String xmlUrl)
    {
        Document document = null;
        DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
        domFactory.setNamespaceAware(true);

        try {
            URL url = new URL(xmlUrl);
            try (
                InputStream inputStream = url.openStream()
            ) {
                DocumentBuilder builder = domFactory.newDocumentBuilder();
                document = builder.parse(inputStream);
            } catch (IOException | ParserConfigurationException | SAXException e) {
                e.printStackTrace();
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }

        return document;
    }
}

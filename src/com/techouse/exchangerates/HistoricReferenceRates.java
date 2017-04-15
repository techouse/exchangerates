package com.techouse.exchangerates;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.*;
import java.sql.Date;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static java.time.temporal.ChronoUnit.DAYS;

class HistoricReferenceRates
{
    private static final String ECB_HISTORIC_CSV_ZIP_URL = "https://www.ecb.europa.eu/stats/eurofxref/eurofxref-hist.zip";
    private static final String CSV_FILENAME = "eurofxref-hist.csv";
    private static final String CSV_DELIMITER = ",";
    private static final String TABLE_NAME = "EURO_EXCHANGE_RATES";
    private static final DataSource datasource = Database.getDataSource();
    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static HistoricReferenceRates instance = new HistoricReferenceRates();

    private HistoricReferenceRates()
    {
        HistoricReferenceRates.prepareDatabase();
    }

    static void prepareDatabase()
    {
        if (dataTableEmpty() || dataNeedsUpdate()) {
            storeData(getDataFromECB());
        }
    }

    private static Map<LocalDate, Map<String, Double>> getDataFromECB()
    {
        Map<LocalDate, Map<String, Double>> historicReferenceRates = new LinkedHashMap<>();

        try {
            URL zipUrl = new URL(ECB_HISTORIC_CSV_ZIP_URL);
            try (
                ZipInputStream zipInputStream = new ZipInputStream(zipUrl.openStream())
            ) {
                ZipEntry zipEntry;

                while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                    if (zipEntry.getName().equals(CSV_FILENAME)) {
                        try (
                            BufferedReader bufferedReader = new BufferedReader(
                                new InputStreamReader(zipInputStream)
                            )
                        ) {
                            String line;
                            String[] keys = null;
                            String[] values;
                            int index = 0;

                            while ((line = bufferedReader.readLine()) != null) {
                                if (index == 0) {
                                    // populate the header and remove the Date column
                                    keys = line.substring(line.indexOf(CSV_DELIMITER) + 1).split(CSV_DELIMITER);
                                } else {
                                    values = line.split(CSV_DELIMITER);
                                    historicReferenceRates.put(
                                        LocalDate.parse(values[0], dateTimeFormatter),
                                        buildReferenceRates(keys, Arrays.copyOfRange(values, 1, values.length))
                                    );
                                }

                                index++;
                            }
                        } catch (InputMismatchException | IOException e) {
                            e.printStackTrace();
                        }

                        break;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }

        return historicReferenceRates;
    }

    private static Map<String, Double> buildReferenceRates(String[] keys, String[] values) throws InputMismatchException
    {
        if (keys.length != values.length) {
            throw new InputMismatchException("Keys and values lengths do not match.");
        }

        Map<String, Double> referenceRates = new HashMap<String, Double>()
        {{
            put(ReferenceRates.REFERENCE_CURRENCY, 1d);
        }};

        for (int i = 0; i < keys.length; i++) {
            referenceRates.put(
                keys[i],
                DataUtils.isNumeric(values[i]) ? Double.parseDouble(values[i]) : 0d
            );
        }

        return referenceRates;
    }

    private static void storeData(Map<LocalDate, Map<String, Double>> historicReferenceRates)
    {
        StringBuilder sql;

        try (
            final Connection connection = datasource.getConnection();
            Statement statement = connection.createStatement()
        ) {
            ExchangeRateGUI.setPreparingDatabase(true);

            connection.setAutoCommit(false);

            if (!Database.tableExists(connection, TABLE_NAME.toUpperCase())) {
                sql = new StringBuilder("CREATE CACHED TABLE IF NOT EXISTS ");
                sql.append(TABLE_NAME);
                sql.append(" (");
                sql.append("date DATE NOT NULL, ");
                sql.append("currency CHAR(3) NOT NULL, ");
                sql.append("value DOUBLE DEFAULT 0 NOT NULL, ");
                sql.append("PRIMARY KEY (date, currency)");
                sql.append(")");
                statement.addBatch(sql.toString());

                sql = new StringBuilder("CREATE INDEX currency ON ");
                sql.append(TABLE_NAME);
                sql.append(" (currency)");
                statement.addBatch(sql.toString());

                statement.executeBatch();
                connection.commit();
            }

            sql = new StringBuilder("INSERT INTO ");
            sql.append(TABLE_NAME);
            sql.append(" (date, currency, value) VALUES (?, ?, ?)");

            StringBuilder dupeCheckSql = new StringBuilder("SELECT 1 AS entry_exists FROM ");
            dupeCheckSql.append(TABLE_NAME);
            dupeCheckSql.append(" WHERE date = ? AND currency = ?");

            try (
                PreparedStatement preparedInsertStatement = connection.prepareStatement(sql.toString());
                PreparedStatement preparedSelectStatement = connection.prepareStatement(dupeCheckSql.toString());
            ) {
                for (LocalDate date : historicReferenceRates.keySet()) {
                    for (String currency : historicReferenceRates.get(date).keySet()) {
                        preparedSelectStatement.setDate(1, Date.valueOf(date));
                        preparedSelectStatement.setString(2, currency);
                        try (ResultSet dupeCheckResult = preparedSelectStatement.executeQuery()) {
                            if (!dupeCheckResult.isBeforeFirst()) {
                                preparedInsertStatement.setDate(1, Date.valueOf(date));
                                preparedInsertStatement.setString(2, currency);
                                preparedInsertStatement.setDouble(3, historicReferenceRates.get(date).get(currency));
                                preparedInsertStatement.executeUpdate();
                            }
                        }
                    }
                }
                connection.commit();
            }

            ExchangeRateGUI.setPreparingDatabase(false);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    static Map<LocalDate, Map<String, Double>> getCompleteDataFromDatabase()
    {
        Map<LocalDate, Map<String, Double>> historicReferenceRates = new LinkedHashMap<>();

        try (final Connection connection = datasource.getConnection()) {
            if (Database.tableExists(connection, TABLE_NAME.toUpperCase())) {
                try (Statement statement = connection.createStatement()) {
                    StringBuilder sql = new StringBuilder("SELECT date, currency, value FROM ");
                    sql.append(TABLE_NAME);

                    try (ResultSet resultSet = statement.executeQuery(sql.toString())) {
                        LocalDate referenceRatesDate = null;
                        Map<String, Double> dailyReferenceRates = new TreeMap<>();
                        while (resultSet.next()) {
                            if (referenceRatesDate == null || resultSet.getDate("date").toLocalDate() != referenceRatesDate) {
                                if (!dailyReferenceRates.isEmpty()) {
                                    historicReferenceRates.put(referenceRatesDate, dailyReferenceRates);
                                }
                                referenceRatesDate = resultSet.getDate("date").toLocalDate();
                                dailyReferenceRates = new TreeMap<>();
                            }
                            dailyReferenceRates.put(
                                resultSet.getString("currency"),
                                resultSet.getDouble("value")
                            );
                        }
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return historicReferenceRates;
    }

    private static boolean dataNeedsUpdate()
    {
        try (final Connection connection = datasource.getConnection()) {
            if (Database.tableExists(connection, TABLE_NAME.toUpperCase())) {
                try (Statement statement = connection.createStatement()) {
                    StringBuilder sql = new StringBuilder("SELECT MAX(date) AS max_date FROM ");
                    sql.append(TABLE_NAME);

                    try (ResultSet resultSet = statement.executeQuery(sql.toString())) {
                        if (resultSet.next()) {
                            LocalDateTime now = LocalDateTime.now(ZoneId.of("Europe/Berlin"));
                            LocalDate maxDate = resultSet.getDate("max_date").toLocalDate();
                            if (resultSet.getDate("max_date").toLocalDate().isBefore(now.toLocalDate())) {
                                long dateDiff = DAYS.between(maxDate, now.toLocalDate());
                                if (dateDiff == 0) {
                                    return false;
                                } else if (dateDiff == 1) {
                                    return now.getHour() > 16 || (now.getHour() == 16 && now.getMinute() > 1);
                                } else if (dateDiff > 1 && dateDiff < 7) {
                                    return !(DayOfWeek.FRIDAY == maxDate.getDayOfWeek() &&
                                            Arrays.asList(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY).contains(now.getDayOfWeek()));
                                } else if (dateDiff > 7) {
                                    return true;
                                } else {
                                    return true;
                                }
                            } else {
                                return false;
                            }
                        } else {
                            return true;
                        }
                    }
                }
            } else {
                return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return true;
        }
    }

    private static boolean dataTableEmpty()
    {
        try (final Connection connection = datasource.getConnection()) {
            if (Database.tableExists(connection, TABLE_NAME.toUpperCase())) {
                try (Statement statement = connection.createStatement()) {
                    StringBuilder sql = new StringBuilder("SELECT COUNT(*) AS num_rows FROM ");
                    sql.append(TABLE_NAME);

                    try (ResultSet resultSet = statement.executeQuery(sql.toString())) {
                        if (resultSet.next()) {
                            return resultSet.getInt("num_rows") <= 0;
                        } else {
                            return true;
                        }
                    }
                }
            } else {
                return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return true;
        }
    }

    static Map<LocalDate, Double> getCurrencyHistory()
    {
        return getCurrencyHistory(ReferenceRates.REFERENCE_CURRENCY, ReferenceRates.REFERENCE_CURRENCY);
    }

    static Map<LocalDate, Double> getCurrencyHistory(String currency)
    {
        return getCurrencyHistory(currency, ReferenceRates.REFERENCE_CURRENCY);
    }

    static Map<LocalDate, Double> getCurrencyHistory(String currency, String baseCurrency)
    {
        Map<LocalDate, Double> rates = new TreeMap<>();
        StringBuilder sql;

        try (final Connection connection = datasource.getConnection()) {
            if (Database.tableExists(connection, TABLE_NAME.toUpperCase())) {
                if (baseCurrency.equals(ReferenceRates.REFERENCE_CURRENCY)) {
                    sql = new StringBuilder("SELECT date, value FROM ");
                    sql.append(TABLE_NAME);
                    sql.append(" WHERE currency = ? AND value > 0 ORDER BY date ASC");
                } else {
                    sql = new StringBuilder("SELECT t1.date, ROUND(t1.value / t2.value, 4) AS value FROM ");
                    sql.append(TABLE_NAME);
                    sql.append(" AS t1 INNER JOIN ");
                    sql.append(TABLE_NAME);
                    sql.append(" AS t2 ON t1.date = t2.date AND t2.currency = ? WHERE t1.currency = ? AND t1.value > 0 ORDER BY t1.date ASC");
                }

                try (PreparedStatement preparedStatement = connection.prepareStatement(sql.toString())) {
                    if (baseCurrency.equals(ReferenceRates.REFERENCE_CURRENCY)) {
                        preparedStatement.setString(1, currency.toUpperCase());
                    } else {
                        preparedStatement.setString(1, baseCurrency.toUpperCase());
                        preparedStatement.setString(2, currency.toUpperCase());
                    }

                    try (ResultSet resultSet = preparedStatement.executeQuery()) {
                        while (resultSet.next()) {
                            rates.put(
                                resultSet.getDate("date").toLocalDate(),
                                resultSet.getDouble("value")
                            );
                        }
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return rates;
    }

    static Map<String, Double> getRates()
    {
        return getRates(LocalDate.now());
    }

    static Map<String, Double> getRates(LocalDate localDate)
    {
        Map<String, Double> rates = new TreeMap<>();

        try (final Connection connection = datasource.getConnection()) {
            if (Database.tableExists(connection, TABLE_NAME.toUpperCase())) {
                StringBuilder sql = new StringBuilder("SELECT currency, value FROM ");
                sql.append(TABLE_NAME);
                sql.append(" WHERE date = ? ORDER BY currency ASC");

                try (PreparedStatement preparedStatement = connection.prepareStatement(sql.toString())) {
                    preparedStatement.setDate(1, Date.valueOf(localDate));

                    try (ResultSet resultSet = preparedStatement.executeQuery()) {
                        while (resultSet.next()) {
                            rates.put(
                                resultSet.getString("currency"),
                                resultSet.getDouble("value")
                            );
                        }
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return rates;
    }

    static double getCurrencyRate(String currency)
    {
        return getCurrencyRate(currency, LocalDate.now());
    }

    static double getCurrencyRate(String currency, LocalDate localDate)
    {
        try (final Connection connection = datasource.getConnection()) {
            if (Database.tableExists(connection, TABLE_NAME.toUpperCase())) {
                StringBuilder sql = new StringBuilder("SELECT value FROM ");
                sql.append(TABLE_NAME);
                sql.append(" WHERE currency = ? AND date <= ? ORDER BY date DESC LIMIT 1");

                try (PreparedStatement preparedStatement = connection.prepareStatement(sql.toString())) {
                    preparedStatement.setString(1, currency);
                    preparedStatement.setDate(2, Date.valueOf(localDate));

                    try (ResultSet resultSet = preparedStatement.executeQuery()) {
                        if (resultSet.next()) {
                            return resultSet.getDouble("value");
                        }
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return 0d;
    }
}

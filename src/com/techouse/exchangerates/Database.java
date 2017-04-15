package com.techouse.exchangerates;

import org.hsqldb.jdbc.JDBCDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.file.*;
import java.nio.file.attribute.DosFileAttributes;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

class Database
{
    private static final String JDBC_DRIVER = "org.hsqldb.jdbc.JDBCDriver";
    private static final String DB_PROTOCOL = "jdbc:hsqldb:file:";
    private static final String DB_NAME = "ExchangeRatesDB";
    private static final String DB_USER = "techouse";
    private static final String DB_PASS = "techmouse";

    private static Database instance = null;
    private DataSource dataSource = null;

    private Database()
    {
        try {
            Class.forName(JDBC_DRIVER);

            StringBuilder databaseURL = new StringBuilder(DB_PROTOCOL);
            Path databaseDir;
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                databaseDir = FileSystems
                    .getDefault()
                    .getPath(
                        ExchangeRateGUI
                            .class
                            .getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .getPath()
                            .substring(1) // Windows needs this this substring because it adds a beginning forward slash
                    )
                    .getParent()
                    .resolve("data");
                try {
                    Files.createDirectory(databaseDir);
                    Files.setAttribute(databaseDir, "dos:hidden", Boolean.TRUE, LinkOption.NOFOLLOW_LINKS);
                } catch (FileAlreadyExistsException ignored) {
                    try {
                        DosFileAttributes attr = Files.readAttributes(databaseDir, DosFileAttributes.class);
                        if (!attr.isHidden()) {
                            Files.setAttribute(databaseDir, "dos:hidden", Boolean.TRUE, LinkOption.NOFOLLOW_LINKS);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                databaseDir = FileSystems
                    .getDefault()
                    .getPath(
                        ExchangeRateGUI
                            .class
                            .getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .getPath()
                    )
                    .getParent()
                    .resolve(".data");
                try {
                    Files.createDirectory(databaseDir);
                } catch (FileAlreadyExistsException ignored) {
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            databaseDir = databaseDir.resolve(DB_NAME);
            databaseURL.append(URLDecoder.decode(databaseDir.toString(), "UTF-8"));

            JDBCDataSource jdbcDataSource = new JDBCDataSource();
            jdbcDataSource.setURL(databaseURL.toString());
            jdbcDataSource.setUser(DB_USER);
            jdbcDataSource.setPassword(DB_PASS);

            this.dataSource = jdbcDataSource;
        } catch (ClassNotFoundException | UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    static DataSource getDataSource()
    {
        if (instance == null) {
            instance = new Database();
        }
        return instance.dataSource;
    }

    static Connection getConnection()
    {
        Connection cnx = null;
        try {
            cnx = getDataSource().getConnection();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return cnx;
    }

    static boolean tableExists(Connection connection, String table)
    {
        try (ResultSet resultSet = connection.getMetaData().getTables(null, null, table.toUpperCase(), null)) {
            return resultSet.next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}

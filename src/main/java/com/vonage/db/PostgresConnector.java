package com.vonage.db;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.function.Function;

@Slf4j
@Component
public class PostgresConnector {

    private static String URL = "jdbc:postgresql://global-pgbouncer.amz1.vocalocity.com:6543/hdap";
    @Value("${USERNAME}")
    private static String USER;
    @Value("${DB_PASS}")
    private String PASSWORD;

    public  <R> R execute(String query, Function<ResultSet, R> resultProcessor) {
        try(Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery(query)){
            return resultProcessor.apply(resultSet);
        } catch (SQLException e) {
            log.error("Connection failure. {}", e.getMessage());
        }
        return null;
    }
}
package com.vonage.db;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.function.Function;

@Slf4j
@Component
public class PostgresConnector {

    private static final String URL = "jdbc:postgresql://global-pgbouncer.amz1.vocalocity.com:6543/hdap";
    private static final String USER = "mpandey";
    private static final String PASSWORD = "vrmkGTKi22";

    public  <R> R execute(String query, Function<ResultSet, R> resultProcessor) {
        try(Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery(query)){
            return resultProcessor.apply(resultSet);
        } catch (SQLException e) {
            log.error("Connection failure.");
        }
        return null;
    }
}
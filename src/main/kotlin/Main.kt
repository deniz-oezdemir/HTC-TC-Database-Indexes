package org.example

import java.sql.DriverManager
import kotlin.system.measureTimeMillis

fun main() {
    // H2 in-memory database connection string
    val dbUrl = "jdbc:h2:mem:testdb"
    Class.forName("org.h2.Driver")
    // Use try-with-resources to ensure the connection is closed automatically
    DriverManager.getConnection(dbUrl).use { connection ->
        println("Database connection established. Populating table...")

        val statement = connection.createStatement()

        // 1. Create Users table
        statement.execute("CREATE TABLE Users (id INT PRIMARY KEY, name VARCHAR(255), email VARCHAR(255))")

        // 2. Populate with a large number of users
        val userCount = 1_000                       // <- manipulate for simulation
        val targetEmail = "user500@example.com"     // <- manipulate for simulation

        // Use a prepared statement for efficient batch inserts
        // A critical point for this demo:
        // We are inserting users in a perfectly sorted loop (user1, user2, user3...).
        // This means the data on disk HAPPENS to be physically sorted by email.
        // HOWEVER, the database engine CANNOT assume this. Without an index, it has
        // no formal guarantee of order and must plan for a "worst-case" scenario
        // where data is unordered. Therefore, it MUST perform a full table scan.
        // An index is what provides the official, unbreakable guarantee of order.
        val insertSql = "INSERT INTO Users (id, name, email) VALUES (?, ?, ?)"
        connection.prepareStatement(insertSql).use { preparedStatement ->
            for (i in 1..userCount) {
                preparedStatement.setInt(1, i)
                preparedStatement.setString(2, "User$i")
                preparedStatement.setString(3, "user$i@example.com")
                preparedStatement.addBatch()
            }
            preparedStatement.executeBatch()
        }
        println("$userCount users inserted successfully.")
        println("--------------------------------------------------")

        // 3. Search WITHOUT an index
        val timeWithoutIndex = measureTimeMillis {
            val rs = statement.executeQuery("SELECT * FROM Users WHERE email = '$targetEmail'")
            if (rs.next()) {
                println("Found user (without index): ${rs.getString("name")}")
            }
        }
        println("Time taken WITHOUT index: $timeWithoutIndex ms\n")
        println("--------------------------------------------------")

        // 4. Create an index on the email column
        println("Creating index on email column...")
        val indexCreationTime = measureTimeMillis {
            statement.execute("CREATE INDEX idx_email ON Users(email)")
        }
        println("Index created successfully in $indexCreationTime ms.")
        println("--------------------------------------------------")

        // 5. Search WITH an index
        val timeWithIndex = measureTimeMillis {
            val rs = statement.executeQuery("SELECT * FROM Users WHERE email = '$targetEmail'")
            if (rs.next()) {
                println("Found user (with index): ${rs.getString("name")}")
            }
        }
        println("Time taken WITH index: $timeWithIndex ms")
        println("--------------------------------------------------")

        val performanceGain = (timeWithoutIndex.toDouble() / timeWithIndex.toDouble()).toInt()
        println("🎉 Query with index was approximately $performanceGain times faster! 🎉")
    }
}

/*
 * 
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * 
 */

package com.micromux.cassandra.jdbc;

import java.net.URLEncoder;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;

import com.micromux.cassandra.ConnectionDetails;
import org.apache.commons.lang3.StringUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class SpashScreenTest
{
    private static final String HOST = System.getProperty("host", ConnectionDetails.getHost());
    private static final int PORT = Integer.parseInt(System.getProperty("port", ConnectionDetails.getPort()+""));
    private static final String KEYSPACE = "testks";
    
    private static java.sql.Connection con = null;

    // use these for encyrpted connections
    private static final String TRUST_STORE = System.getProperty("trustStore");
    private static final String TRUST_PASS = System.getProperty("trustPass", "cassandra");

    private static String OPTIONS = "";

    @BeforeClass
    public static void setUpBeforeClass() throws Exception
    {
        // configure OPTIONS
        if (!StringUtils.isEmpty(TRUST_STORE)) {
            OPTIONS = String.format("trustStore=%s&trustPass=%s",
                    URLEncoder.encode(TRUST_STORE), TRUST_PASS);
        }

        Class.forName("com.micromux.cassandra.jdbc.CassandraDriver");
        con = DriverManager.getConnection(String.format("jdbc:cassandra://%s:%d/%s?%s&version=3.0.0",HOST,PORT,"system",OPTIONS));
        Statement stmt = con.createStatement();

        // Drop Keyspace
        String dropKS = String.format("DROP KEYSPACE %s;",KEYSPACE);
        
        try { stmt.execute(dropKS);}
        catch (Exception e){/* Exception on DROP is OK */}
        
        // Create KeySpace
        String createKS = String.format("CREATE KEYSPACE \"%s\" WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};",KEYSPACE);
        stmt = con.createStatement();
        stmt.execute(createKS);
        
        // Use Keyspace
        String useKS = String.format("USE %s;",KEYSPACE);
        stmt.execute(useKS);
        
               
        // Create the target Column family
        String create = "CREATE COLUMNFAMILY Test (KEY text PRIMARY KEY, a bigint, b bigint) ;";
        stmt = con.createStatement();
        stmt.execute(create);
        stmt.close();
        con.close();

        // open it up again to see the new CF
        con = DriverManager.getConnection(String.format("jdbc:cassandra://%s:%d/%s?%s",HOST,PORT,KEYSPACE,OPTIONS));
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception
    {
        if (con!=null) con.close();
    }


    @Test
    public void test() throws Exception
    {
        String query = "UPDATE Test SET a=?, b=? WHERE KEY=?";
        PreparedStatement statement = con.prepareStatement(query);
        try {
        statement.setLong(1, 100);
        statement.setLong(2, 1000);
        statement.setString(3, "key0");

        statement.executeUpdate();
        }
        finally {
        statement.close();
    }
}
}

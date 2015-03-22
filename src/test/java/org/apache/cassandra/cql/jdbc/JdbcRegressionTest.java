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
package org.apache.cassandra.cql.jdbc;

import org.apache.cassandra.cql.ConnectionDetails;
import org.apache.cassandra.thrift.ConsistencyLevel;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.CharBuffer;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class JdbcRegressionTest
{
    private static final String HOST = System.getProperty("host", ConnectionDetails.getHost());
    private static final int PORT = Integer.parseInt(System.getProperty("port", ConnectionDetails.getPort()+""));
    private static final String KEYSPACE = "testks";
    private static final String TABLE = "regressiontest";
    private static final String TYPETABLE = "datatypetest";
//    private static final String CQLV3 = "3.0.0";
    private static final String CONSISTENCY_QUORUM = "QUORUM";
      
    private static java.sql.Connection con = null;
    
    @BeforeClass
    public static void setUpBeforeClass() throws Exception
    {
        Class.forName("org.apache.cassandra.cql.jdbc.CassandraDriver");
        
        con = createNewConnection("system");
        Statement stmt = con.createStatement();
        
        // Drop Keyspace
        String dropKS = String.format("DROP KEYSPACE \"%s\";",KEYSPACE);
        
        try { stmt.execute(dropKS);}
        catch (Exception e){/* Exception on DROP is OK */}

        // Create KeySpace
        String createKS = String.format("CREATE KEYSPACE \"%s\" WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};",KEYSPACE);
        System.out.println("createKS = '"+createKS+"'");
        stmt = con.createStatement();
        stmt.execute("USE system;");
        stmt.execute(createKS);
        
        // Use Keyspace
        String useKS = String.format("USE \"%s\";",KEYSPACE);
        stmt.execute(useKS);
        
        // Create the target Column family
        String createCF = "CREATE COLUMNFAMILY "+TABLE+" (keyname text PRIMARY KEY,"
                        + " bValue boolean,"
                        + " iValue int"
                        + ");";
        stmt.execute(createCF);

        //create an index
        stmt.execute("CREATE INDEX ON "+TABLE+" (iValue)");

        String createCF2 = "CREATE COLUMNFAMILY " + TYPETABLE + " ( "
                + " id uuid PRIMARY KEY, "
                + " blobValue blob,"
                + " blobSetValue set<blob>,"
                + " dataMapValue map<text,blob>,"
                + " uuidList list<uuid>,"
                + " stringMap map<text,text> "
                + ") WITH comment = 'datatype TABLE in the Keyspace'"
                + ";";
        stmt.execute(createCF2);

        stmt.close();
        con.close();

        // open it up again to see the new CF
        con = createNewConnection(KEYSPACE);
        System.out.println(con);

    }

	private static Connection createNewConnection(String ks) throws SQLException 
	{
		String URL = String.format("jdbc:cassandra://%s:%d/%s",HOST,PORT,ks);
        System.out.println("Connection URL = '"+URL +"'");
		return DriverManager.getConnection(URL);
	}

    @AfterClass
    public static void tearDownAfterClass() throws Exception
    {
        if (con!=null) con.close();
    }


    @Test
    public void testIssue10() throws Exception
    {
        String insert = "INSERT INTO regressiontest (keyname,bValue,iValue) VALUES( 'key0',true, 2000);";
        Statement statement = con.createStatement();

        statement.executeUpdate(insert);
        statement.close();
        
        statement = con.createStatement();
        ResultSet result = statement.executeQuery("SELECT bValue,iValue FROM regressiontest WHERE keyname='key0';");
        result.next();
        
        boolean b = result.getBoolean(1);
        assertTrue(b);
        
        int i = result.getInt(2);
        assertEquals(2000, i);
   }

    @Test
    public void testIssue15() throws Exception
    {
//        con.close();
//
//        con = DriverManager.getConnection(String.format("jdbc:cassandra://%s:%d/%s?version=%s",HOST,PORT,KEYSPACE,CQLV3));
//        System.out.println(con);
//        con.close();

//        con = DriverManager.getConnection(String.format("jdbc:cassandra://%s:%d/%s",HOST,PORT,KEYSPACE));
//        System.out.println(con);
//        con.close();

    }
    
    @Test
    public void testIssue18() throws Exception
    {
       Statement statement = con.createStatement();

       String truncate = "TRUNCATE regressiontest;";
       statement.execute(truncate);
       
       String insert1 = "INSERT INTO regressiontest (keyname,bValue,iValue) VALUES( 'key0',true, 2000);";
       statement.executeUpdate(insert1);
       
       String insert2 = "INSERT INTO regressiontest (keyname,bValue) VALUES( 'key1',false);";
       statement.executeUpdate(insert2);
       
       
       
       String select = "SELECT * from regressiontest;";
       
       ResultSet result = statement.executeQuery(select);
       
       ResultSetMetaData metadata = result.getMetaData();
       
       int colCount = metadata.getColumnCount();
       
       System.out.println("Before doing a next()");
       System.out.printf("(%d) ",result.getRow());
       for (int i = 1; i <= colCount; i++)
       {
           System.out.print(showColumn(i,result)+ " "); 
       }
       System.out.println();
       
       
       System.out.println("Fetching each row with a next()");
       while (result.next())
       {
           metadata = result.getMetaData();
           colCount = metadata.getColumnCount();
           System.out.printf("(%d) ",result.getRow());
           for (int i = 1; i <= colCount; i++)
           {
               System.out.print(showColumn(i,result)+ " "); 
           }
           System.out.println();
       }
    }
    
    @Test
    public void testIssue33() throws Exception
    {
        Statement stmt = con.createStatement();
        
        // Create the target Column family
        String createCF = "CREATE COLUMNFAMILY t33 (k int PRIMARY KEY," 
                        + "c text "
                        + ") ;";        
        
        stmt.execute(createCF);
        stmt.close();
        con.close();

        // open it up again to see the new CF
        con = createNewConnection(KEYSPACE);
        
        // paraphrase of the snippet from the ISSUE #33 provided test
        PreparedStatement statement = con.prepareStatement("update t33 set c=? where k=123");
        statement.setString(1, "mark");
        statement.executeUpdate();

        ResultSet result = statement.executeQuery("SELECT * FROM t33;");
        
        ResultSetMetaData metadata = result.getMetaData();
        
        int colCount = metadata.getColumnCount();
        
        System.out.println("Test Issue #33");
        System.out.println("--------------");
        while (result.next())
        {
            metadata = result.getMetaData();
            colCount = metadata.getColumnCount();
            System.out.printf("(%d) ",result.getRow());
            for (int i = 1; i <= colCount; i++)
            {
                System.out.print(showColumn(i,result)+ " "); 
            }
            System.out.println();
        }
   }

    @Test
    public void testIssue38() throws Exception
    {
        DatabaseMetaData md = con.getMetaData();
        System.out.println();
        System.out.println("Test Issue #38");
        System.out.println("--------------");
        System.out.println("Driver Version :   " + md.getDriverVersion());
        System.out.println("DB Version     :   " + md.getDatabaseProductVersion());
        System.out.println("Catalog term   :   " + md.getCatalogTerm());
        System.out.println("Catalog        :   " + con.getCatalog());
        System.out.println("Schema term    :   " + md.getSchemaTerm());
        
        // test catching exception for beforeFirst() and afterLast()
        Statement stmt = con.createStatement();

        ResultSet result = stmt.executeQuery("SELECT * FROM t33;");
        
        try
        {
            result.beforeFirst();
        }
        catch (Exception e)
        {
            System.out.println();
            System.out.println("beforeFirst() test -> "+ e);
        }
        
    }
    
    @Test
    public void testIssue40() throws Exception
    {
        DatabaseMetaData md = con.getMetaData();
        System.out.println();
        System.out.println("Test Issue #40");
        System.out.println("--------------");

        // test various retrieval methods
        ResultSet result = md.getTables(con.getCatalog(), null, "%", new String[]
        { "TABLE" });
        assertTrue("Make sure we have found a table", result.next());
        result = md.getTables(null, KEYSPACE, TABLE, null);
        assertTrue("Make sure we have found the table asked for", result.next());
        result = md.getTables(null, KEYSPACE, TABLE, new String[]
        { "TABLE" });
        assertTrue("Make sure we have found the table asked for", result.next());
        result = md.getTables(con.getCatalog(), KEYSPACE, TABLE, new String[]
        { "TABLE" });
        assertTrue("Make sure we have found the table asked for", result.next());

        // check the table name
        String tn = result.getString("TABLE_NAME");
        assertEquals("Table name match", TABLE, tn);
        System.out.println("Found table via dmd    :   " + tn);

        // load the columns
        result = md.getColumns(con.getCatalog(), KEYSPACE, TABLE, null);
        assertTrue("Make sure we have found first column", result.next());
        assertEquals("Make sure table name match", TABLE, result.getString("TABLE_NAME"));
        String cn = result.getString("COLUMN_NAME");
        System.out.println("Found (default) PK column       :   " + cn);
        assertEquals("Column name check", "keyname", cn);
        assertEquals("Column type check", Types.VARCHAR, result.getInt("DATA_TYPE"));
        assertTrue("Make sure we have found second column", result.next());
        cn = result.getString("COLUMN_NAME");
        System.out.println("Found column       :   " + cn);
        assertEquals("Column name check", "bvalue", cn);
        assertEquals("Column type check", Types.BOOLEAN, result.getInt("DATA_TYPE"));
        assertTrue("Make sure we have found thirth column", result.next());
        cn = result.getString("COLUMN_NAME");
        System.out.println("Found column       :   " + cn);
        assertEquals("Column name check", "ivalue", cn);
        assertEquals("Column type check", Types.INTEGER, result.getInt("DATA_TYPE"));

        // make sure we filter
        result = md.getColumns(con.getCatalog(), KEYSPACE, TABLE, "bvalue");
        result.next();
        assertFalse("Make sure we have found requested column only", result.next());
    }    
    
    @Test
    public void testIssue59() throws Exception
    {
        Statement stmt = con.createStatement();
        
        // Create the target Column family
        String createCF = "CREATE COLUMNFAMILY t59 (k int PRIMARY KEY," 
                        + "c text "
                        + ") ;";        
        
        stmt.execute(createCF);
        stmt.close();
        con.close();

        // open it up again to see the new CF
        con = createNewConnection(KEYSPACE);
 
        PreparedStatement statement = con.prepareStatement("update t59 set c=? where k=123", ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
        statement.setString(1, "hello");
        statement.executeUpdate();

        ResultSet result = statement.executeQuery("SELECT * FROM t59;");
        
        System.out.println(resultToDisplay(result,59,null));

    }
    
    @Test
    public void testIssue65() throws Exception
    {
        Statement stmt = con.createStatement();
        
        // Create the target Column family
        String createCF = "CREATE COLUMNFAMILY t65 (key text PRIMARY KEY," 
                        + "int1 int, "
                        + "int2 int, "
                        + "intset  set<int> "
                        + ") ;";        
        
        stmt.execute(createCF);
        stmt.close();
        con.close();

        // open it up again to see the new CF
        con = createNewConnection(KEYSPACE);
        
        Statement statement = con.createStatement();
        String insert = "INSERT INTO t65 (key, int1,int2,intset) VALUES ('key1',1,100,{10,20,30,40});";
        statement.executeUpdate(insert);
        
        ResultSet result = statement.executeQuery("SELECT * FROM t65;");

        System.out.println(resultToDisplay(result,65, "with set = {10,20,30,40}"));
       
        String update = "UPDATE t65 SET intset=? WHERE key=?;";
 
        PreparedStatement pstatement = con.prepareStatement(update);
        Set<Integer> mySet = new HashSet<Integer> ();
        pstatement.setObject(1, mySet, Types.OTHER);
        pstatement.setString(2, "key1");
       
        pstatement.executeUpdate();

        result = statement.executeQuery("SELECT * FROM t65;");
        
        System.out.println(resultToDisplay(result,65," with set = <empty>"));

    }
    
    @Test
    public void testIssue71() throws Exception
    {
        Statement stmt = con.createStatement();
        
        // Create the target Column family
        String createCF = "CREATE COLUMNFAMILY t71 (k int PRIMARY KEY," 
                        + "c text "
                        + ") ;";        
        
        stmt.execute(createCF);
        stmt.close();
        con.close();

        // open it up again to see the new CF
       con = DriverManager.getConnection(String.format("jdbc:cassandra://%s:%d/%s?consistency=%s",HOST,PORT,KEYSPACE,CONSISTENCY_QUORUM));
      
       // at this point defaultConsistencyLevel should be set the QUORUM in the connection

       stmt = con.createStatement();
       
       ConsistencyLevel cl = statementExtras(stmt).getConsistencyLevel();
       assertTrue(ConsistencyLevel.QUORUM == cl );
       
       System.out.println();
       System.out.println("Test Issue #71");
       System.out.println("--------------");
       System.out.println("statement.consistencyLevel = "+ cl);
       


    }
    
    @Test
    public void testIssue74() throws Exception
    {
        Statement stmt = con.createStatement();
        java.util.Date now = new java.util.Date();

        
        // Create the target Column family
        String createCF = "CREATE COLUMNFAMILY t74 (id BIGINT PRIMARY KEY, col1 TIMESTAMP)";        
        
        stmt.execute(createCF);
        stmt.close();
        con.close();

        // open it up again to see the new CF
        con = createNewConnection(KEYSPACE);
        
        Statement statement = con.createStatement();
        
        String insert = "INSERT INTO t74 (id, col1) VALUES (?, ?);";
        
        PreparedStatement pstatement = con.prepareStatement(insert);
        pstatement.setLong(1, 1L); 
        pstatement.setObject(2, new Timestamp(now.getTime()),Types.TIMESTAMP);
        pstatement.execute();

        ResultSet result = statement.executeQuery("SELECT * FROM t74;");
        
        assertTrue(result.next());
        assertEquals(1L, result.getLong(1));
        Timestamp stamp = result.getTimestamp(2);
        
        assertEquals(now, stamp);
        stamp = (Timestamp)result.getObject(2); // maybe exception here
        assertEquals(now, stamp);

        System.out.println(resultToDisplay(result,74, "current date"));
       
    }

    @Test
    public void testColumnTypes() throws Exception
    {
        Statement stmt = con.createStatement();

        // Create the target Column family
        String createCF = "CREATE table ttypes (id BIGINT PRIMARY KEY, col_f float,col_d double,col_n int,col_t timestamp)";        
        
        stmt.execute(createCF);
        stmt.close();
        con.close();

        // open it up again to see the new CF
        con = createNewConnection(KEYSPACE);
        
        Statement statement = con.createStatement();
        
        String insert = "INSERT INTO ttypes (id, col_f,col_d,col_n,col_t) VALUES (?, ?, ?, ?,?);";
        
        PreparedStatement pstatement = con.prepareStatement(insert);
        pstatement.setLong(1, 1L); 
        pstatement.setFloat(2, 1f); 
        pstatement.setDouble(3, 1d); 
        pstatement.setInt(4, 1); 
        pstatement.setDate(5, new java.sql.Date(System.currentTimeMillis())); 
        pstatement.execute();

        pstatement.setObject(1, new Long(2l)); 
        pstatement.setObject(2, new Float(1f)); 
        pstatement.setObject(3, new Double(1d)); 
        pstatement.setObject(4, new Integer(1)); 
        pstatement.setObject(5, new Date()); 
        pstatement.execute();

        pstatement.setObject(1, new Long(3l),Types.BIGINT); 
        pstatement.setObject(2, new Float(1f),Types.FLOAT);
        pstatement.setObject(3, new Double(1d),Types.DOUBLE); 
        pstatement.setObject(4, new Integer(1),Types.INTEGER);
        pstatement.setObject(5, new Date(),Types.TIMESTAMP); 
        pstatement.execute();

        ResultSet result = statement.executeQuery("SELECT * FROM ttypes;");
        assertTrue(result.next());
        assertTrue(result.next());
        assertTrue(result.next());
    }

    @Test
    public void testIssue75() throws Exception
    {
        System.out.println();
        System.out.println("Test Issue #75");
        System.out.println("--------------");

        Statement stmt = con.createStatement();

        String truncate = "TRUNCATE regressiontest;";
        stmt.execute(truncate);

        String select = "select ivalue from "+TABLE;        
        
        ResultSet result = stmt.executeQuery(select);
        assertFalse("Make sure we have no rows", result.next());
        ResultSetMetaData rsmd = result.getMetaData();
        assertTrue("Make sure we do get a result", rsmd.getColumnDisplaySize(1) != 0);
        assertNotNull("Make sure we do get a label",rsmd.getColumnLabel(1));
        System.out.println("Found a column in ResultsetMetaData even when there are no rows:   " + rsmd.getColumnLabel(1));
        stmt.close();
    }

    @Test
    public void testIssue76() throws Exception
    {
        DatabaseMetaData md = con.getMetaData();
        System.out.println();
        System.out.println("Test Issue #76");
        System.out.println("--------------");

        // test various retrieval methods
        ResultSet result = md.getIndexInfo(con.getCatalog(), KEYSPACE, TABLE, false, false);
        assertTrue("Make sure we have found an index", result.next());

        // check the column name from index
        String cn = result.getString("COLUMN_NAME");
        assertEquals("Column name match for index", "ivalue", cn);
        System.out.println("Found index via dmd on :   " + cn);
    }

    @Test
    public void testIssue77() throws Exception
    {
        DatabaseMetaData md = con.getMetaData();
        System.out.println();
        System.out.println("Test Issue #77");
        System.out.println("--------------");

        // test various retrieval methods
        ResultSet result = md.getPrimaryKeys(con.getCatalog(), KEYSPACE, TABLE);
        assertTrue("Make sure we have found an pk", result.next());

        // check the column name from index
        String cn = result.getString("COLUMN_NAME");
        assertEquals("Column name match for pk", "keyname", cn);
        System.out.println("Found pk via dmd :   " + cn);
    }

    @Test
    public void testIssue78() throws Exception
    {
        DatabaseMetaData md = con.getMetaData();

        // load the columns, with no catalog and schema
        ResultSet result = md.getColumns(null, "%", TABLE, "ivalue");
        assertTrue("Make sure we have found an column", result.next());
    }

    @Test
    public void testBlob() throws Exception
    {
        UUID blobId = UUID.randomUUID();
        String blobValue = RandomStringUtils.random(10);
        String insert = "INSERT INTO " + TYPETABLE + " (id,blobValue,dataMapValue) " +
                        " VALUES(" + blobId.toString() + ", ?, {'12345': bigintAsBlob(12345)});";

        PreparedStatement statement = con.prepareStatement(insert);
        statement.setObject(1, blobValue);

        statement.executeUpdate();
        statement.close();

        Statement select1 = con.createStatement();
        String query = "SELECT blobValue FROM "+TYPETABLE+" WHERE id=" + blobId.toString() + ";";
        ResultSet result = select1.executeQuery(query);
        result.next();

        Object blobResult = result.getObject(1);
        assertEquals(blobValue, blobResult);

        Statement select2 = con.createStatement();
        String query2 = "SELECT dataMapValue FROM "+TYPETABLE+" WHERE id=" + blobId.toString() + ";";
        ResultSet result2 = select2.executeQuery(query2);
        result2.next();

        Object mapObj = result2.getObject(1);
        assertTrue(mapObj instanceof Map);

        Map mapResult = (Map)mapObj;
        assertEquals(1, mapResult.size());



    }
    
    @Test
    public void testListUuid() throws Exception
    {
        UUID id = UUID.randomUUID();
        UUID uuid1 = UUID.randomUUID();

        String insert = "INSERT INTO " + TYPETABLE + " (id,uuidList) " +
                        " VALUES(" + id.toString() + ", [" + uuid1.toString() + "]);";

        PreparedStatement statement = con.prepareStatement(insert);
        statement.executeUpdate();
        statement.close();

        Statement select1 = con.createStatement();
        String query = "SELECT uuidList FROM "+TYPETABLE+" WHERE id=" + id.toString() + ";";
        ResultSet result = select1.executeQuery(query);
        result.next();

        Object objList = result.getObject(1);

        assertTrue("Expecting a List of UUID", objList instanceof List);

        List uuidList = (List)objList;
        assertEquals("List should have (1) element", 1, uuidList.size());
        assertEquals("Element should be equal at (0)", uuid1, uuidList.get(0));

    }

    @Test
    public void testMapString() throws Exception
    {
        UUID id = UUID.randomUUID();

        String key1 = RandomStringUtils.randomAlphanumeric(5);
        String value1 = RandomStringUtils.randomAlphanumeric(99);

        String insert = String.format("INSERT INTO %s (id, stringMap) " +
                        " VALUES(%s, {'%s' : '%s'});",
                            TYPETABLE, id.toString(),
                            key1, value1);

        PreparedStatement statement = con.prepareStatement(insert);
        statement.executeUpdate();
        statement.close();

        Statement select1 = con.createStatement();
        String query = String.format("SELECT stringMap FROM %s WHERE id=%s;", TYPETABLE, id.toString());
        ResultSet result = select1.executeQuery(query);
        result.next();

        Object objMap = result.getObject(1);

        assertTrue("Expecting a Map", objMap instanceof Map);

        Map strMap = (Map)objMap;
        assertEquals("Map should have (1) element", 1, strMap.size());
        assertTrue("Key1 should be in the map", strMap.containsKey(key1));
//        assertTrue("Key1:Value should match expected", value1, (String)(strMap.get(key1)));

    }


    @Test
    public void isValid() throws Exception
    {
//    	assert con.isValid(3);
    }
    
    @Test(expected=SQLException.class)
    public void isValidSubZero() throws Exception
    {
    	con.isValid(-42);
    }
    
    @Test
    public void isNotValid() throws Exception
    {
//        PreparedStatement currentStatement = ((CassandraConnection) con).isAlive;
//        PreparedStatement mockedStatement = mock(PreparedStatement.class);
//        when(mockedStatement.executeQuery()).thenThrow(new SQLException("A mocked ERROR"));
//        ((CassandraConnection) con).isAlive = mockedStatement;
//        assert con.isValid(5) == false;
//        ((CassandraConnection) con).isAlive = currentStatement;
    }
    
    private final String  showColumn(int index, ResultSet result) throws SQLException
    {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(index).append("]");
        sb.append(result.getObject(index));
        return sb.toString();
    }
    
    private final String resultToDisplay(ResultSet result, int issue, String note) throws Exception
    {
        StringBuilder sb = new StringBuilder("Test Issue #" + issue + " - "+ note + "\n");
       ResultSetMetaData metadata = result.getMetaData();
        
        int colCount = metadata.getColumnCount();
        
        sb.append("--------------").append("\n");
        while (result.next())
        {
            metadata = result.getMetaData();
            colCount = metadata.getColumnCount();
            sb.append(String.format("(%d) ",result.getRow()));
            for (int i = 1; i <= colCount; i++)
            {
                sb.append(showColumn(i,result)+ " "); 
            }
            sb.append("\n");
        }
        
        return sb.toString();        
    }
    
    private CassandraStatementExtras statementExtras(Statement statement) throws Exception
    {
        Class cse = Class.forName("org.apache.cassandra.cql.jdbc.CassandraStatementExtras");
        return (CassandraStatementExtras) statement.unwrap(cse);
    }

}

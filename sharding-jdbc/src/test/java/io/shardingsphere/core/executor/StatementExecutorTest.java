/*
 * Copyright 2016-2018 shardingsphere.io.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * </p>
 */

package io.shardingsphere.core.executor;

import io.shardingsphere.core.constant.DatabaseType;
import io.shardingsphere.core.constant.SQLType;
import io.shardingsphere.core.event.ShardingEventType;
import io.shardingsphere.core.executor.sql.execute.threadlocal.ExecutorExceptionHandler;
import io.shardingsphere.core.executor.statement.MemoryStrictlyStatementExecutor;
import io.shardingsphere.core.executor.statement.StatementExecuteUnit;
import io.shardingsphere.core.executor.statement.StatementExecutor;
import io.shardingsphere.core.rewrite.SQLBuilder;
import io.shardingsphere.core.routing.RouteUnit;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public final class StatementExecutorTest extends AbstractBaseExecutorTest {

    private static final String DQL_SQL = "SELECT * FROM table_x";
    
    private static final String DML_SQL = "DELETE FROM table_x";
    
    @Test
    public void assertNoStatement() throws SQLException {
        StatementExecutor actual = new MemoryStrictlyStatementExecutor(DatabaseType.H2, SQLType.DQL, getExecuteTemplate(), Collections.<StatementExecuteUnit>emptyList());
        assertFalse(actual.execute());
        assertThat(actual.executeUpdate(), is(0));
        assertThat(actual.executeQuery().size(), is(0));
    }
    
    @Test
    public void assertExecuteQueryForSingleStatementSuccess() throws SQLException {
        Statement statement = mock(Statement.class);
        ResultSet resultSet = mock(ResultSet.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData databaseMetaData = mock(DatabaseMetaData.class);
        when(databaseMetaData.getURL()).thenReturn("jdbc:h2:mem:test_db");
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        when(statement.executeQuery(DQL_SQL)).thenReturn(resultSet);
        when(statement.getConnection()).thenReturn(connection);
        StatementExecutor actual = new MemoryStrictlyStatementExecutor(DatabaseType.H2, SQLType.DQL, getExecuteTemplate(), createStatementExecuteUnits(DQL_SQL, statement, "ds_0"));
        assertThat(actual.executeQuery(), is(Collections.singletonList(resultSet)));
        verify(statement).executeQuery(DQL_SQL);
        verify(getEventCaller(), times(2)).verifyDataSource("ds_0");
        verify(getEventCaller(), times(2)).verifySQL(DQL_SQL);
        verify(getEventCaller(), times(2)).verifyParameters(Collections.emptyList());
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.BEFORE_EXECUTE);
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.EXECUTE_SUCCESS);
        verify(getEventCaller(), times(0)).verifyException(null);
    }
    
    @Test
    public void assertExecuteQueryForMultipleStatementsSuccess() throws SQLException {
        Statement statement1 = mock(Statement.class);
        Statement statement2 = mock(Statement.class);
        ResultSet resultSet1 = mock(ResultSet.class);
        ResultSet resultSet2 = mock(ResultSet.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData databaseMetaData = mock(DatabaseMetaData.class);
        when(databaseMetaData.getURL()).thenReturn("jdbc:h2:mem:test_db");
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        when(statement1.executeQuery(DQL_SQL)).thenReturn(resultSet1);
        when(statement1.getConnection()).thenReturn(connection);
        when(statement2.executeQuery(DQL_SQL)).thenReturn(resultSet2);
        when(statement2.getConnection()).thenReturn(connection);
        StatementExecutor actual = new MemoryStrictlyStatementExecutor(
                DatabaseType.H2, SQLType.DQL, getExecuteTemplate(), createStatementExecuteUnits(DQL_SQL, statement1, "ds_0", statement2, "ds_1"));
        List<ResultSet> actualResultSets = actual.executeQuery();
        assertThat(actualResultSets, hasItem(resultSet1));
        assertThat(actualResultSets, hasItem(resultSet2));
        verify(statement1).executeQuery(DQL_SQL);
        verify(statement2).executeQuery(DQL_SQL);
        verify(getEventCaller(), times(2)).verifyDataSource("ds_0");
        verify(getEventCaller(), times(2)).verifyDataSource("ds_1");
        verify(getEventCaller(), times(4)).verifySQL(DQL_SQL);
        verify(getEventCaller(), times(4)).verifyParameters(Collections.emptyList());
        verify(getEventCaller(), times(2)).verifyEventExecutionType(ShardingEventType.BEFORE_EXECUTE);
        verify(getEventCaller(), times(2)).verifyEventExecutionType(ShardingEventType.EXECUTE_SUCCESS);
        verify(getEventCaller(), times(0)).verifyException(null);
    }
    
    @Test
    public void assertExecuteQueryForSingleStatementFailure() throws SQLException {
        Statement statement = mock(Statement.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData databaseMetaData = mock(DatabaseMetaData.class);
        when(databaseMetaData.getURL()).thenReturn("jdbc:h2:mem:test_db");
        SQLException exp = new SQLException();
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        when(statement.executeQuery(DQL_SQL)).thenThrow(exp);
        when(statement.getConnection()).thenReturn(connection);
        StatementExecutor actual = new MemoryStrictlyStatementExecutor(DatabaseType.H2, SQLType.DQL, getExecuteTemplate(), createStatementExecuteUnits(DQL_SQL, statement, "ds_0"));
        assertThat(actual.executeQuery(), is(Collections.singletonList((ResultSet) null)));
        verify(statement).executeQuery(DQL_SQL);
        verify(getEventCaller(), times(2)).verifyDataSource("ds_0");
        verify(getEventCaller(), times(2)).verifySQL(DQL_SQL);
        verify(getEventCaller(), times(2)).verifyParameters(Collections.emptyList());
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.BEFORE_EXECUTE);
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.EXECUTE_FAILURE);
        verify(getEventCaller()).verifyException(exp);
    }
    
    @Test
    public void assertExecuteQueryForMultipleStatementsFailure() throws SQLException {
        Statement statement1 = mock(Statement.class);
        Statement statement2 = mock(Statement.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData databaseMetaData = mock(DatabaseMetaData.class);
        when(databaseMetaData.getURL()).thenReturn("jdbc:h2:mem:test_db");
        SQLException exp = new SQLException();
        when(statement1.executeQuery(DQL_SQL)).thenThrow(exp);
        when(statement2.executeQuery(DQL_SQL)).thenThrow(exp);
        when(statement1.getConnection()).thenReturn(connection);
        when(statement2.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        StatementExecutor actual = new MemoryStrictlyStatementExecutor(
                DatabaseType.H2, SQLType.DQL, getExecuteTemplate(), createStatementExecuteUnits(DQL_SQL, statement1, "ds_0", statement2, "ds_1"));
        List<ResultSet> actualResultSets = actual.executeQuery();
        assertThat(actualResultSets, is(Arrays.asList((ResultSet) null, null)));
        verify(statement1).executeQuery(DQL_SQL);
        verify(statement2).executeQuery(DQL_SQL);
        verify(getEventCaller(), times(2)).verifyDataSource("ds_0");
        verify(getEventCaller(), times(2)).verifyDataSource("ds_1");
        verify(getEventCaller(), times(4)).verifySQL(DQL_SQL);
        verify(getEventCaller(), times(4)).verifyParameters(Collections.emptyList());
        verify(getEventCaller(), times(2)).verifyEventExecutionType(ShardingEventType.BEFORE_EXECUTE);
        verify(getEventCaller(), times(2)).verifyEventExecutionType(ShardingEventType.EXECUTE_FAILURE);
        verify(getEventCaller(), times(2)).verifyException(exp);
    }
    
    @Test
    public void assertExecuteUpdateForSingleStatementSuccess() throws SQLException {
        Statement statement = mock(Statement.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData databaseMetaData = mock(DatabaseMetaData.class);
        when(databaseMetaData.getURL()).thenReturn("jdbc:h2:mem:test_db");
        when(statement.executeUpdate(DML_SQL)).thenReturn(10);
        when(statement.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        StatementExecutor actual = new MemoryStrictlyStatementExecutor(DatabaseType.H2, SQLType.DML, getExecuteTemplate(), createStatementExecuteUnits(DML_SQL, statement, "ds_0"));
        assertThat(actual.executeUpdate(), is(10));
        verify(statement).executeUpdate(DML_SQL);
        verify(getEventCaller(), times(2)).verifyDataSource("ds_0");
        verify(getEventCaller(), times(2)).verifySQL(DML_SQL);
        verify(getEventCaller(), times(2)).verifyParameters(Collections.emptyList());
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.BEFORE_EXECUTE);
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.EXECUTE_SUCCESS);
        verify(getEventCaller(), times(0)).verifyException(null);
    }
    
    @Test
    public void assertExecuteUpdateForMultipleStatementsSuccess() throws SQLException {
        Statement statement1 = mock(Statement.class);
        Statement statement2 = mock(Statement.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData databaseMetaData = mock(DatabaseMetaData.class);
        when(databaseMetaData.getURL()).thenReturn("jdbc:h2:mem:test_db");
        when(statement1.executeUpdate(DML_SQL)).thenReturn(10);
        when(statement2.executeUpdate(DML_SQL)).thenReturn(20);
        when(statement1.getConnection()).thenReturn(connection);
        when(statement2.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        StatementExecutor actual = new MemoryStrictlyStatementExecutor(
                DatabaseType.H2, SQLType.DML, getExecuteTemplate(), createStatementExecuteUnits(DML_SQL, statement1, "ds_0", statement2, "ds_1"));
        assertThat(actual.executeUpdate(), is(30));
        verify(statement1).executeUpdate(DML_SQL);
        verify(statement2).executeUpdate(DML_SQL);
        verify(getEventCaller(), times(2)).verifyDataSource("ds_0");
        verify(getEventCaller(), times(2)).verifyDataSource("ds_1");
        verify(getEventCaller(), times(4)).verifySQL(DML_SQL);
        verify(getEventCaller(), times(4)).verifyParameters(Collections.emptyList());
        verify(getEventCaller(), times(2)).verifyEventExecutionType(ShardingEventType.BEFORE_EXECUTE);
        verify(getEventCaller(), times(2)).verifyEventExecutionType(ShardingEventType.EXECUTE_SUCCESS);
        verify(getEventCaller(), times(0)).verifyException(null);
    }
    
    @Test
    public void assertExecuteUpdateForSingleStatementFailure() throws SQLException {
        Statement statement = mock(Statement.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData databaseMetaData = mock(DatabaseMetaData.class);
        when(databaseMetaData.getURL()).thenReturn("jdbc:h2:mem:test_db");
        SQLException exp = new SQLException();
        when(statement.executeUpdate(DML_SQL)).thenThrow(exp);
        when(statement.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        StatementExecutor actual = new MemoryStrictlyStatementExecutor(DatabaseType.H2, SQLType.DML, getExecuteTemplate(), createStatementExecuteUnits(DML_SQL, statement, "ds_0"));
        assertThat(actual.executeUpdate(), is(0));
        verify(statement).executeUpdate(DML_SQL);
        verify(getEventCaller(), times(2)).verifyDataSource("ds_0");
        verify(getEventCaller(), times(2)).verifySQL(DML_SQL);
        verify(getEventCaller(), times(2)).verifyParameters(Collections.emptyList());
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.BEFORE_EXECUTE);
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.EXECUTE_FAILURE);
        verify(getEventCaller()).verifyException(exp);
    }
    
    @Test
    public void assertExecuteUpdateForMultipleStatementsFailure() throws SQLException {
        Statement statement1 = mock(Statement.class);
        Statement statement2 = mock(Statement.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData databaseMetaData = mock(DatabaseMetaData.class);
        when(databaseMetaData.getURL()).thenReturn("jdbc:h2:mem:test_db");
        SQLException exp = new SQLException();
        when(statement1.executeUpdate(DML_SQL)).thenThrow(exp);
        when(statement2.executeUpdate(DML_SQL)).thenThrow(exp);
        when(statement1.getConnection()).thenReturn(connection);
        when(statement2.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        StatementExecutor actual = new MemoryStrictlyStatementExecutor(
                DatabaseType.H2, SQLType.DML, getExecuteTemplate(), createStatementExecuteUnits(DML_SQL, statement1, "ds_0", statement2, "ds_1"));
        assertThat(actual.executeUpdate(), is(0));
        verify(statement1).executeUpdate(DML_SQL);
        verify(statement2).executeUpdate(DML_SQL);
        verify(getEventCaller(), times(2)).verifyDataSource("ds_0");
        verify(getEventCaller(), times(2)).verifyDataSource("ds_1");
        verify(getEventCaller(), times(4)).verifySQL(DML_SQL);
        verify(getEventCaller(), times(4)).verifyParameters(Collections.emptyList());
        verify(getEventCaller(), times(2)).verifyEventExecutionType(ShardingEventType.BEFORE_EXECUTE);
        verify(getEventCaller(), times(2)).verifyEventExecutionType(ShardingEventType.EXECUTE_FAILURE);
        verify(getEventCaller(), times(2)).verifyException(exp);
    }
    
    @Test
    public void assertExecuteUpdateWithAutoGeneratedKeys() throws SQLException {
        Statement statement = mock(Statement.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData databaseMetaData = mock(DatabaseMetaData.class);
        when(databaseMetaData.getURL()).thenReturn("jdbc:h2:mem:test_db");
        when(statement.executeUpdate(DML_SQL, Statement.NO_GENERATED_KEYS)).thenReturn(10);
        when(statement.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        StatementExecutor actual = new MemoryStrictlyStatementExecutor(DatabaseType.H2, SQLType.DML, getExecuteTemplate(), createStatementExecuteUnits(DML_SQL, statement, "ds_0"));
        assertThat(actual.executeUpdate(Statement.NO_GENERATED_KEYS), is(10));
        verify(statement).executeUpdate(DML_SQL, Statement.NO_GENERATED_KEYS);
        verify(getEventCaller(), times(2)).verifyDataSource("ds_0");
        verify(getEventCaller(), times(2)).verifySQL(DML_SQL);
        verify(getEventCaller(), times(2)).verifyParameters(Collections.emptyList());
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.BEFORE_EXECUTE);
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.EXECUTE_SUCCESS);
        verify(getEventCaller(), times(0)).verifyException(null);
    }
    
    @Test
    public void assertExecuteUpdateWithColumnIndexes() throws SQLException {
        Statement statement = mock(Statement.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData databaseMetaData = mock(DatabaseMetaData.class);
        when(statement.executeUpdate(DML_SQL, new int[] {1})).thenReturn(10);
        when(statement.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        when(databaseMetaData.getURL()).thenReturn("jdbc:h2:mem:test_db");
        StatementExecutor actual = new MemoryStrictlyStatementExecutor(DatabaseType.H2, SQLType.DML, getExecuteTemplate(), createStatementExecuteUnits(DML_SQL, statement, "ds_0"));
        assertThat(actual.executeUpdate(new int[] {1}), is(10));
        verify(statement).executeUpdate(DML_SQL, new int[] {1});
        verify(getEventCaller(), times(2)).verifyDataSource("ds_0");
        verify(getEventCaller(), times(2)).verifySQL(DML_SQL);
        verify(getEventCaller(), times(2)).verifyParameters(Collections.emptyList());
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.BEFORE_EXECUTE);
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.EXECUTE_SUCCESS);
        verify(getEventCaller(), times(0)).verifyException(null);
    }
    
    @Test
    public void assertExecuteUpdateWithColumnNames() throws SQLException {
        Statement statement = mock(Statement.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData databaseMetaData = mock(DatabaseMetaData.class);
        when(statement.executeUpdate(DML_SQL, new String[] {"col"})).thenReturn(10);
        when(statement.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        when(databaseMetaData.getURL()).thenReturn("jdbc:h2:mem:test_db");
        StatementExecutor actual = new MemoryStrictlyStatementExecutor(DatabaseType.H2, SQLType.DML, getExecuteTemplate(), createStatementExecuteUnits(DML_SQL, statement, "ds_0"));
        assertThat(actual.executeUpdate(new String[] {"col"}), is(10));
        verify(statement).executeUpdate(DML_SQL, new String[] {"col"});
        verify(getEventCaller(), times(2)).verifyDataSource("ds_0");
        verify(getEventCaller(), times(2)).verifySQL(DML_SQL);
        verify(getEventCaller(), times(2)).verifyParameters(Collections.emptyList());
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.BEFORE_EXECUTE);
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.EXECUTE_SUCCESS);
        verify(getEventCaller(), times(0)).verifyException(null);
    }
    
    @Test
    public void assertExecuteForSingleStatementSuccessWithDML() throws SQLException {
        Statement statement = mock(Statement.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData databaseMetaData = mock(DatabaseMetaData.class);
        when(databaseMetaData.getURL()).thenReturn("jdbc:h2:mem:test_db");
        when(statement.execute(DML_SQL)).thenReturn(false);
        when(statement.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        StatementExecutor actual = new MemoryStrictlyStatementExecutor(DatabaseType.H2, SQLType.DML, getExecuteTemplate(), createStatementExecuteUnits(DML_SQL, statement, "ds_0"));
        assertFalse(actual.execute());
        verify(statement).execute(DML_SQL);
        verify(getEventCaller(), times(2)).verifyDataSource("ds_0");
        verify(getEventCaller(), times(2)).verifySQL(DML_SQL);
        verify(getEventCaller(), times(2)).verifyParameters(Collections.emptyList());
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.BEFORE_EXECUTE);
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.EXECUTE_SUCCESS);
        verify(getEventCaller(), times(0)).verifyException(null);
    }
    
    @Test
    public void assertExecuteForMultipleStatementsSuccessWithDML() throws SQLException {
        Statement statement1 = mock(Statement.class);
        Statement statement2 = mock(Statement.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData databaseMetaData = mock(DatabaseMetaData.class);
        when(databaseMetaData.getURL()).thenReturn("jdbc:h2:mem:test_db");
        when(statement1.execute(DML_SQL)).thenReturn(false);
        when(statement2.execute(DML_SQL)).thenReturn(false);
        when(statement1.getConnection()).thenReturn(connection);
        when(statement2.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        StatementExecutor actual = new MemoryStrictlyStatementExecutor(
                DatabaseType.H2, SQLType.DML, getExecuteTemplate(), createStatementExecuteUnits(DML_SQL, statement1, "ds_0", statement2, "ds_1"));
        assertFalse(actual.execute());
        verify(statement1).execute(DML_SQL);
        verify(statement2).execute(DML_SQL);
        verify(getEventCaller(), times(2)).verifyDataSource("ds_0");
        verify(getEventCaller(), times(2)).verifyDataSource("ds_1");
        verify(getEventCaller(), times(4)).verifySQL(DML_SQL);
        verify(getEventCaller(), times(4)).verifyParameters(Collections.emptyList());
        verify(getEventCaller(), times(2)).verifyEventExecutionType(ShardingEventType.BEFORE_EXECUTE);
        verify(getEventCaller(), times(2)).verifyEventExecutionType(ShardingEventType.EXECUTE_SUCCESS);
        verify(getEventCaller(), times(0)).verifyException(null);
    }
    
    @Test
    public void assertExecuteForSingleStatementFailureWithDML() throws SQLException {
        Statement statement = mock(Statement.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData databaseMetaData = mock(DatabaseMetaData.class);
        when(databaseMetaData.getURL()).thenReturn("jdbc:h2:mem:test_db");
        SQLException exp = new SQLException();
        when(statement.execute(DML_SQL)).thenThrow(exp);
        when(statement.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        StatementExecutor actual = new MemoryStrictlyStatementExecutor(DatabaseType.H2, SQLType.DML, getExecuteTemplate(), createStatementExecuteUnits(DML_SQL, statement, "ds_0"));
        assertFalse(actual.execute());
        verify(statement).execute(DML_SQL);
        verify(getEventCaller(), times(2)).verifyDataSource("ds_0");
        verify(getEventCaller(), times(2)).verifySQL(DML_SQL);
        verify(getEventCaller(), times(2)).verifyParameters(Collections.emptyList());
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.BEFORE_EXECUTE);
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.EXECUTE_FAILURE);
        verify(getEventCaller()).verifyException(exp);
    }
    
    @Test
    public void assertExecuteForMultipleStatementsFailureWithDML() throws SQLException {
        Statement statement1 = mock(Statement.class);
        Statement statement2 = mock(Statement.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData databaseMetaData = mock(DatabaseMetaData.class);
        when(databaseMetaData.getURL()).thenReturn("jdbc:h2:mem:test_db");
        SQLException exp = new SQLException();
        when(statement1.execute(DML_SQL)).thenThrow(exp);
        when(statement2.execute(DML_SQL)).thenThrow(exp);
        when(statement1.getConnection()).thenReturn(connection);
        when(statement2.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        StatementExecutor actual = new MemoryStrictlyStatementExecutor(
                DatabaseType.H2, SQLType.DML, getExecuteTemplate(), createStatementExecuteUnits(DML_SQL, statement1, "ds_0", statement2, "ds_1"));
        assertFalse(actual.execute());
        verify(statement1).execute(DML_SQL);
        verify(statement2).execute(DML_SQL);
        verify(getEventCaller(), times(2)).verifyDataSource("ds_0");
        verify(getEventCaller(), times(2)).verifyDataSource("ds_1");
        verify(getEventCaller(), times(4)).verifySQL(DML_SQL);
        verify(getEventCaller(), times(4)).verifyParameters(Collections.emptyList());
        verify(getEventCaller(), times(2)).verifyEventExecutionType(ShardingEventType.BEFORE_EXECUTE);
        verify(getEventCaller(), times(2)).verifyEventExecutionType(ShardingEventType.EXECUTE_FAILURE);
        verify(getEventCaller(), times(2)).verifyException(exp);
    }
    
    @Test
    public void assertExecuteForSingleStatementWithDQL() throws SQLException {
        Statement statement = mock(Statement.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData databaseMetaData = mock(DatabaseMetaData.class);
        when(databaseMetaData.getURL()).thenReturn("jdbc:h2:mem:test_db");
        when(statement.execute(DQL_SQL)).thenReturn(true);
        when(statement.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        StatementExecutor actual = new MemoryStrictlyStatementExecutor(DatabaseType.H2, SQLType.DQL, getExecuteTemplate(), createStatementExecuteUnits(DQL_SQL, statement, "ds_0"));
        assertTrue(actual.execute());
        verify(statement).execute(DQL_SQL);
        verify(getEventCaller(), times(2)).verifyDataSource("ds_0");
        verify(getEventCaller(), times(2)).verifySQL(DQL_SQL);
        verify(getEventCaller(), times(2)).verifyParameters(Collections.emptyList());
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.BEFORE_EXECUTE);
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.EXECUTE_SUCCESS);
        verify(getEventCaller(), times(0)).verifyException(null);
    }
    
    @Test
    public void assertExecuteForMultipleStatements() throws SQLException {
        Statement statement1 = mock(Statement.class);
        Statement statement2 = mock(Statement.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData databaseMetaData = mock(DatabaseMetaData.class);
        when(databaseMetaData.getURL()).thenReturn("jdbc:h2:mem:test_db");
        when(statement1.execute(DQL_SQL)).thenReturn(true);
        when(statement2.execute(DQL_SQL)).thenReturn(true);
        when(statement1.getConnection()).thenReturn(connection);
        when(statement2.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        StatementExecutor actual = new MemoryStrictlyStatementExecutor(
                DatabaseType.H2, SQLType.DQL, getExecuteTemplate(), createStatementExecuteUnits(DQL_SQL, statement1, "ds_0", statement2, "ds_1"));
        assertTrue(actual.execute());
        verify(statement1).execute(DQL_SQL);
        verify(statement2).execute(DQL_SQL);
        verify(getEventCaller(), times(2)).verifyDataSource("ds_0");
        verify(getEventCaller(), times(2)).verifyDataSource("ds_1");
        verify(getEventCaller(), times(4)).verifySQL(DQL_SQL);
        verify(getEventCaller(), times(4)).verifyParameters(Collections.emptyList());
        verify(getEventCaller(), times(2)).verifyEventExecutionType(ShardingEventType.BEFORE_EXECUTE);
        verify(getEventCaller(), times(2)).verifyEventExecutionType(ShardingEventType.EXECUTE_SUCCESS);
        verify(getEventCaller(), times(0)).verifyException(null);
    }
    
    @Test
    public void assertExecuteWithAutoGeneratedKeys() throws SQLException {
        Statement statement = mock(Statement.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData databaseMetaData = mock(DatabaseMetaData.class);
        when(databaseMetaData.getURL()).thenReturn("jdbc:h2:mem:test_db");
        when(statement.execute(DML_SQL, Statement.NO_GENERATED_KEYS)).thenReturn(false);
        when(statement.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        StatementExecutor actual = new MemoryStrictlyStatementExecutor(DatabaseType.H2, SQLType.DML, getExecuteTemplate(), createStatementExecuteUnits(DML_SQL, statement, "ds_0"));
        assertFalse(actual.execute(Statement.NO_GENERATED_KEYS));
        verify(statement).execute(DML_SQL, Statement.NO_GENERATED_KEYS);
        verify(getEventCaller(), times(2)).verifyDataSource("ds_0");
        verify(getEventCaller(), times(2)).verifySQL(DML_SQL);
        verify(getEventCaller(), times(2)).verifyParameters(Collections.emptyList());
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.BEFORE_EXECUTE);
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.EXECUTE_SUCCESS);
        verify(getEventCaller(), times(0)).verifyException(null);
    }
    
    @Test
    public void assertExecuteWithColumnIndexes() throws SQLException {
        Statement statement = mock(Statement.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData databaseMetaData = mock(DatabaseMetaData.class);
        when(databaseMetaData.getURL()).thenReturn("jdbc:h2:mem:test_db");
        when(statement.execute(DML_SQL, new int[] {1})).thenReturn(false);
        when(statement.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        StatementExecutor actual = new MemoryStrictlyStatementExecutor(DatabaseType.H2, SQLType.DML, getExecuteTemplate(), createStatementExecuteUnits(DML_SQL, statement, "ds_0"));
        assertFalse(actual.execute(new int[] {1}));
        verify(statement).execute(DML_SQL, new int[] {1});
        verify(getEventCaller(), times(2)).verifyDataSource("ds_0");
        verify(getEventCaller(), times(2)).verifySQL(DML_SQL);
        verify(getEventCaller(), times(2)).verifyParameters(Collections.emptyList());
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.BEFORE_EXECUTE);
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.EXECUTE_SUCCESS);
        verify(getEventCaller(), times(0)).verifyException(null);
    }
    
    @Test
    public void assertExecuteWithColumnNames() throws SQLException {
        Statement statement = mock(Statement.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData databaseMetaData = mock(DatabaseMetaData.class);
        when(databaseMetaData.getURL()).thenReturn("jdbc:h2:mem:test_db");
        when(statement.execute(DML_SQL, new String[] {"col"})).thenReturn(false);
        when(statement.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        StatementExecutor actual = new MemoryStrictlyStatementExecutor(DatabaseType.H2, SQLType.DML, getExecuteTemplate(), createStatementExecuteUnits(DML_SQL, statement, "ds_0"));
        assertFalse(actual.execute(new String[] {"col"}));
        verify(statement).execute(DML_SQL, new String[] {"col"});
        verify(getEventCaller(), times(2)).verifyDataSource("ds_0");
        verify(getEventCaller(), times(2)).verifySQL(DML_SQL);
        verify(getEventCaller(), times(2)).verifyParameters(Collections.emptyList());
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.BEFORE_EXECUTE);
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.EXECUTE_SUCCESS);
        verify(getEventCaller(), times(0)).verifyException(null);
    }
    
    @Test
    public void assertOverallExceptionFailure() throws SQLException {
        ExecutorExceptionHandler.setExceptionThrown(true);
        Statement statement = mock(Statement.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData databaseMetaData = mock(DatabaseMetaData.class);
        when(databaseMetaData.getURL()).thenReturn("jdbc:h2:mem:test_db");
        SQLException exp = new SQLException();
        when(statement.execute(DML_SQL)).thenThrow(exp);
        when(statement.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        StatementExecutor actual = new MemoryStrictlyStatementExecutor(DatabaseType.H2, SQLType.DML, getExecuteTemplate(), createStatementExecuteUnits(DML_SQL, statement, "ds_0"));
        try {
            assertFalse(actual.execute());
        } catch (final SQLException ignore) {
        }
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.BEFORE_EXECUTE);
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.EXECUTE_FAILURE);
    }
    
    private Collection<StatementExecuteUnit> createStatementExecuteUnits(final String sql, final Statement statement, final String dataSource) {
        Collection<StatementExecuteUnit> result = new LinkedList<>();
        SQLBuilder sqlBuilder = new SQLBuilder();
        sqlBuilder.appendLiterals(sql);
        result.add(new StatementExecuteUnit(new RouteUnit(dataSource, sqlBuilder.toSQL(null, Collections.<String, String>emptyMap(), null, null)), statement));
        return result;
    }
    
    private Collection<StatementExecuteUnit> createStatementExecuteUnits(
            final String sql, final Statement statement1, final String dataSource1, final Statement statement2, final String dataSource2) {
        Collection<StatementExecuteUnit> result = new LinkedList<>();
        result.addAll(createStatementExecuteUnits(sql, statement1, dataSource1));
        result.addAll(createStatementExecuteUnits(sql, statement2, dataSource2));
        return result;
    }
}

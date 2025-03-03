/*
 * Copyright 2021 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.common.apps.storage.sql.jdbi;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import io.apicurio.common.apps.storage.exceptions.StorageException;
import io.apicurio.common.apps.storage.sql.jdbi.mappers.IntegerMapper;
import io.apicurio.common.apps.storage.sql.jdbi.mappers.LongMapper;
import io.apicurio.common.apps.storage.sql.jdbi.mappers.StringMapper;

/**
 * @author eric.wittmann@gmail.com
 */
public class QueryImpl extends SqlImpl<Query> implements Query {

    private int fetchSize = -1;

    /**
     * Constructor.
     * @param connection a DB connection
     * @param sql some SQL statement(s)
     */
    public QueryImpl(Connection connection, String sql) {
        super(connection, sql);
    }

    /**
     * @see io.apicurio.common.apps.storage.sql.jdbi.Query#setFetchSize(int)
     */
    @Override
    public Query setFetchSize(int size) {
        this.fetchSize = size;
        return this;
    }

    /**
     * @see io.apicurio.common.apps.storage.sql.jdbi.Query#map(io.apicurio.common.apps.storage.sql.jdbi.RowMapper)
     */
    @Override
    public <T> MappedQuery<T> map(RowMapper<T> mapper) {
        try {
            PreparedStatement statement = this.connection.prepareStatement(sql);
            this.bindParametersTo(statement);
            if (this.fetchSize != -1) {
                statement.setFetchSize(fetchSize);
            }
            return new MappedQueryImpl<T>(statement, mapper);
        } catch (SQLException e) {
            throw new StorageException(e);
        }
    }

    /**
     * @see io.apicurio.common.apps.storage.sql.jdbi.Query#mapTo(java.lang.Class)
     */
    @Override
    public <T> MappedQuery<T> mapTo(Class<T> someClass) {
        RowMapper<T> mapper = this.createMapper(someClass);
        return this.map(mapper);
    }

    @SuppressWarnings("unchecked")
    private <T> RowMapper<T> createMapper(Class<T> someClass) {
        if (someClass == Long.class) {
            return (RowMapper<T>) LongMapper.instance;
        } else if (someClass == Integer.class) {
            return (RowMapper<T>) IntegerMapper.instance;
        } else if (someClass == String.class) {
            return (RowMapper<T>) StringMapper.instance;
        } else {
            throw new StorageException("Row mapper not implemented for class: " + someClass);
        }
    }

}

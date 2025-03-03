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

import java.io.Closeable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import io.apicurio.common.apps.storage.exceptions.StorageException;

/**
 * @author eric.wittmann@gmail.com
 */
public class MappedQueryImpl<T> implements MappedQuery<T>, Closeable {

    final PreparedStatement statement;
    final RowMapper<T> mapper;
    final ResultSet resultSet;

    /**
     * Constructor.
     * @param statement a SQL prepared statement
     * @param mapper a row mapper
     * @throws SQLException if a SQL error is detected
     */
    public MappedQueryImpl(PreparedStatement statement, RowMapper<T> mapper) throws SQLException {
        this.statement = statement;
        this.mapper = mapper;
        this.resultSet = statement.executeQuery();
    }

    /**
     * @see io.apicurio.common.apps.storage.sql.jdbi.MappedQuery#one()
     */
    @Override
    public T one() {
        T rval = null;
        try {
            if (this.resultSet.next()) {
                rval = this.mapper.map(resultSet);
                if (this.resultSet.next()) {
                    throw new StorageException("SQL error: Expected only one result but got multiple.");
                }
            } else {
                throw new StorageException("SQL error: Expected only one result row but got none.");
            }
        } catch (SQLException e) {
            throw new StorageException(e);
        } finally {
            close();
        }
        return rval;
    }

    /**
     * @see io.apicurio.common.apps.storage.sql.jdbi.MappedQuery#first()
     */
    @Override
    public T first() {
        T rval = null;
        try {
            if (this.resultSet.next()) {
                rval = this.mapper.map(resultSet);
            } else {
                throw new StorageException("SQL error: Expected AT LEAST one result row but got none.");
            }
        } catch (SQLException e) {
            throw new StorageException(e);
        } finally {
            close();
        }
        return rval;
    }

    /**
     * @see io.apicurio.common.apps.storage.sql.jdbi.MappedQuery#findOne()
     */
    @Override
    public Optional<T> findOne() {
        Optional<T> rval;
        try {
            if (this.resultSet.next()) {
                rval = Optional.of(this.mapper.map(resultSet));
                if (this.resultSet.next()) {
                    throw new StorageException("SQL error: Expected only one result but got multiple.");
                }
            } else {
                rval = Optional.empty();
            }
        } catch (SQLException e) {
            throw new StorageException(e);
        } finally {
            close();
        }
        return rval;
    }

    /**
     * @see io.apicurio.common.apps.storage.sql.jdbi.MappedQuery#findFirst()
     */
    @Override
    public Optional<T> findFirst() {
        Optional<T> rval = null;
        try {
            if (this.resultSet.next()) {
                rval = Optional.of(this.mapper.map(resultSet));
            } else {
                rval = Optional.empty();
            }
        } catch (SQLException e) {
            throw new StorageException(e);
        } finally {
            close();
        }
        return rval;
    }

    /**
     * @see io.apicurio.common.apps.storage.sql.jdbi.MappedQuery#list()
     */
    @Override
    public List<T> list() {
        List<T> rval = new LinkedList<>();
        try {
            while (this.resultSet.next()) {
                T t = this.mapper.map(resultSet);
                rval.add(t);
            }
        } catch (SQLException e) {
            throw new StorageException(e);
        } finally {
            close();
        }
        return rval;
    }

    /**
     * @see io.apicurio.common.apps.storage.sql.jdbi.MappedQuery#stream()
     */
    @Override
    public Stream<T> stream() {
        return StreamSupport.stream(new Spliterators.AbstractSpliterator<T>(Long.MAX_VALUE, Spliterator.IMMUTABLE | Spliterator.ORDERED | Spliterator.DISTINCT | Spliterator.NONNULL) {
            @Override
            public boolean tryAdvance(Consumer<? super T> action) {
                try {
                    if (!resultSet.next()) {
                        return false;
                    }
                    T t = mapper.map(resultSet);
                    action.accept(t);
                    return true;
                } catch (SQLException e) {
                    throw new StorageException(e);
                }
            }

        }, false).onClose(this::close);
    }

    /**
     * @see java.io.Closeable#close()
     */
    @Override
    public void close() {
        try {
            this.statement.close();
        } catch (SQLException e) {
            throw new StorageException(e);
        }
    }

}

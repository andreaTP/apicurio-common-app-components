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

/**
 * @author eric.wittmann@gmail.com
 */
public interface Query extends Sql<Query> {

    public Query setFetchSize(int size);

    public <T> MappedQuery<T> map(RowMapper<T> mapper);

    public <T> MappedQuery<T> mapTo(Class<T> someClass);

}

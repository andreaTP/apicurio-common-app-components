/*
 * Copyright 2022 Red Hat
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

package io.apicurio.common.apps.config;

import java.time.Instant;
import java.util.List;

/**
 * @author eric.wittmann@gmail.com
 */
public interface DynamicConfigStorage {

    /**
     * Should return the stored config property or null if not found.
     * @param propertyName the name of a property
     * @return the property value or null if not found
     */
    public DynamicConfigPropertyDto getConfigProperty(String propertyName);

    public void setConfigProperty(DynamicConfigPropertyDto propertyDto);

    public void deleteConfigProperty(String propertyName);

    public List<String> getTenantsWithStaleConfigProperties(Instant lastRefresh);

    public List<DynamicConfigPropertyDto> getConfigProperties();

}

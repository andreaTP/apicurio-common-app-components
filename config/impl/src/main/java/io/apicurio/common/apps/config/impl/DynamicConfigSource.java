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

package io.apicurio.common.apps.config.impl;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import org.eclipse.microprofile.config.spi.ConfigSource;

import io.apicurio.common.apps.config.DynamicConfigPropertyDto;
import io.apicurio.common.apps.config.DynamicConfigStorage;

/**
 * A microprofile-config configsource.  This class uses the dynamic config storage to
 * read/write configuration properties to, for example, a database.
 * <p>
 * TODO cache properties.  this would need to be multi-tenant aware?  probably should be implemented in the storage layer!
 *
 * @author eric.wittmann@gmail.com
 */
public class DynamicConfigSource implements ConfigSource {

    private static Optional<DynamicConfigStorage> storage = Optional.empty();
    public static void setStorage(DynamicConfigStorage configStorage) {
        storage = Optional.of(configStorage);
    }
    private static Optional<DynamicConfigPropertyIndexImpl> configIndex = Optional.empty();
    public static void setConfigurationIndex(DynamicConfigPropertyIndexImpl index) {
        configIndex = Optional.of(index);
    }

    @Override
    public int getOrdinal() {
        return 450; // Very high ordinal value:  https://quarkus.io/guides/config-reference#configuration-sources
    }

    /**
     * @see org.eclipse.microprofile.config.spi.ConfigSource#getPropertyNames()
     */
    @Override
    public Set<String> getPropertyNames() {
        return Collections.emptySet();
    }

    /**
     * @see org.eclipse.microprofile.config.spi.ConfigSource#getValue(java.lang.String)
     */
    @Override
    public String getValue(String propertyName) {
        String pname = normalizePropertyName(propertyName);
        if (configIndex.isPresent() && configIndex.get().hasProperty(pname) && storage.isPresent()) {
            DynamicConfigPropertyDto dto = storage.get().getConfigProperty(pname);
            if (dto != null) {
                return dto.getValue();
            }
        }
        return null;
    }

    private String normalizePropertyName(String propertyName) {
        if (propertyName == null || !propertyName.startsWith("%")) {
            return propertyName;
        }
        int idx = propertyName.indexOf(".");
        if (idx >= propertyName.length()) {
            return propertyName;
        }
        return propertyName.substring(idx + 1);
    }

    /**
     * @see org.eclipse.microprofile.config.spi.ConfigSource#getName()
     */
    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

}

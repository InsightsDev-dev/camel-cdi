/**
 * Copyright (C) 2014 Antonin Stefanutti (antonin.stefanutti@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.cdi;

import org.apache.camel.spi.Registry;
import org.apache.camel.util.ObjectHelper;

import javax.enterprise.inject.Vetoed;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Vetoed
final class CdiBeanRegistry implements Registry {

    private final BeanManager manager;

    CdiBeanRegistry(BeanManager manager) {
        this.manager = manager;
    }

    @Override
    public Object lookupByName(String name) {
        ObjectHelper.notEmpty(name, "name");
        return BeanManagerHelper.getReferenceByName(manager, name, Object.class);
    }

    @Override
    public <T> T lookupByNameAndType(String name, Class<T> type) {
        ObjectHelper.notEmpty(name, "name");
        ObjectHelper.notNull(type, "type");
        return BeanManagerHelper.getReferenceByName(manager, name, type);
    }

    @Override
    public <T> Map<String, T> findByTypeWithName(Class<T> type) {
        ObjectHelper.notNull(type, "type");
        Map<String, T> references = new HashMap<>();
        for (Bean<?> bean : manager.getBeans(type, AnyLiteral.INSTANCE))
            // FIXME: check if the bean has the @Named qualifier instead
            if (bean.getName() != null)
                references.put(bean.getName(), BeanManagerHelper.<T>getReference(manager, type, bean));

        return references;
    }

    @Override
    public <T> Set<T> findByType(Class<T> type) {
        ObjectHelper.notNull(type, "type");
        return BeanManagerHelper.getReferencesByType(manager, type, AnyLiteral.INSTANCE);
    }

    @Override
    public Object lookup(String name) {
        return lookupByName(name);
    }

    @Override
    public <T> T lookup(String name, Class<T> type) {
        return lookupByNameAndType(name, type);
    }

    @Override
    public <T> Map<String, T> lookupByType(Class<T> type) {
        return findByTypeWithName(type);
    }

    @Override
    public String toString() {
        return "CDI bean registry";
    }
}
/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.cdi;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Producer;
import org.apache.camel.cdi.xml.CamelProxyFactoryDefinition;
import org.apache.camel.component.bean.ProxyHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.CreationException;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import java.util.function.Function;

import static org.apache.camel.cdi.BeanManagerHelper.getReference;
import static org.apache.camel.cdi.BeanManagerHelper.getReferenceByName;
import static org.apache.camel.util.ObjectHelper.isNotEmpty;

final class XmlProxyFactoryBean<T> extends SyntheticBean<T> {

    private final BeanManager manager;

    private final Bean<?> context;

    private final CamelProxyFactoryDefinition proxy;

    private Producer producer;

    XmlProxyFactoryBean(BeanManager manager, SyntheticAnnotated annotated, Class<?> type, Function<Bean<T>, String> toString, Bean<?> context, CamelProxyFactoryDefinition proxy) {
        super(manager, annotated, type, null, toString);
        this.manager = manager;
        this.context = context;
        this.proxy = proxy;
    }

    @Override
    public T create(CreationalContext<T> creationalContext) {
        try {
            CamelContext context = isNotEmpty(proxy.getCamelContextId())
                ? getReferenceByName(manager, proxy.getCamelContextId(), CamelContext.class).get()
                : getReference(manager, CamelContext.class, this.context);

            Endpoint endpoint;
            if (ObjectHelper.isNotEmpty(proxy.getServiceRef()))
                endpoint = context.getRegistry().lookupByNameAndType(proxy.getServiceRef(), Endpoint.class);
            else if (ObjectHelper.isNotEmpty(proxy.getServiceUrl()))
                endpoint = context.getEndpoint(proxy.getServiceUrl());
            else
                throw new CreationException("serviceUrl or serviceRef must not be empty!");

            if (endpoint == null)
                throw new CreationException("Could not resolve endpoint: "
                    + (ObjectHelper.isNotEmpty(proxy.getServiceRef())
                    ? proxy.getServiceRef()
                    : proxy.getServiceUrl()));

            // binding is enabled by default
            boolean bind = proxy.getBinding() != null ? proxy.getBinding() : true;

            producer = endpoint.createProducer();
            ServiceHelper.startService(producer);
            return ProxyHelper.createProxy(endpoint, bind, producer, (Class<T>) proxy.getServiceInterface());
        } catch (Exception cause) {
            throw new CreationException("Error while creating instance for " + this, cause);
        }
    }

    @Override
    public void destroy(T instance, CreationalContext<T> creationalContext) {
        try {
            ServiceHelper.stopService(producer);
        } catch (Exception cause) {
            throw ObjectHelper.wrapRuntimeCamelException(cause);
        }
    }
}
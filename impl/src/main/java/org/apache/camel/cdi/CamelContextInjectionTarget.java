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
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.InjectionTarget;

final class CamelContextInjectionTarget<T extends CamelContext> extends CamelContextProducer<T> implements InjectionTarget<T> {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final InjectionTarget<T> delegate;

    CamelContextInjectionTarget(InjectionTarget<T> delegate, Annotated type, BeanManager manager) {
        super(delegate, type, manager);
        this.delegate = delegate;
    }

    @Override
    public T produce(CreationalContext<T> ctx) {
        return super.produce(ctx);
    }

    @Override
    public void inject(T instance, CreationalContext<T> ctx) {
        delegate.inject(instance, ctx);
    }

    @Override
    public void postConstruct(T instance) {
        delegate.postConstruct(instance);
    }

    @Override
    public void preDestroy(T instance) {
        delegate.preDestroy(instance);
        if (!instance.getStatus().isStopped()) {
            logger.info("Camel CDI is stopping {}", instance);
            try {
                instance.stop();
            } catch (Exception cause) {
                throw ObjectHelper.wrapRuntimeCamelException(cause);
            }
        }
    }
}
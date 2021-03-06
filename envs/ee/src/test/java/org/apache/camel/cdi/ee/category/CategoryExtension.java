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
package org.apache.camel.cdi.ee.category;

import org.jboss.arquillian.container.test.spi.client.deployment.AuxiliaryArchiveAppender;
import org.jboss.arquillian.core.spi.LoadableExtension;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;

public class CategoryExtension implements LoadableExtension {

    public void register(LoadableExtension.ExtensionBuilder builder) {
        builder.service(AuxiliaryArchiveAppender.class, CategoryArchiveAppender.class);
    }

    private static final class CategoryArchiveAppender implements AuxiliaryArchiveAppender {

        @Override
        public Archive<?> createAuxiliaryArchive() {
            return ShrinkWrap.create(JavaArchive.class, "categories.jar")
                .addPackage(CategoryExtension.class.getPackage());
        }
    }
}
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

import javax.enterprise.inject.Vetoed;
import javax.enterprise.inject.spi.BeanAttributes;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Vetoed
final class CdiSpiHelper {

    static Class<?> getRawType(Type type) {
        if (type instanceof Class<?>) {
            return Class.class.cast(type);
        }
        else if (type instanceof ParameterizedType) {
            return getRawType(ParameterizedType.class.cast(type).getRawType());
        }
        else if (type instanceof TypeVariable<?>) {
            return getBound(TypeVariable.class.cast(type).getBounds());
        }
        else if (type instanceof WildcardType) {
            return getBound(WildcardType.class.cast(type).getUpperBounds());
        }
        else if (type instanceof GenericArrayType) {
            Class<?> rawType = getRawType(GenericArrayType.class.cast(type).getGenericComponentType());
            if (rawType != null)
                return Array.newInstance(rawType, 0).getClass();
        }
        throw new UnsupportedOperationException("Unable to retrieve raw type for [" + type + "]");
    }

    private static Class<?> getBound(Type[] bounds) {
        if (bounds.length == 0)
            return Object.class;
        else
            return getRawType(bounds[0]);
    }

    /**
     * Generates a unique signature for {@link BeanAttributes}.
     */
    static String createBeanAttributesId(BeanAttributes<?> attributes) {
        return Stream.of(attributes.getName(),
                attributes.getScope().getName(),
                createAnnotationCollectionId(attributes.getQualifiers()),
                createTypeCollectionId(attributes.getTypes()))
            .filter(s -> s != null)
            .collect(Collectors.joining(","));
    }

    /**
     * Generates a unique signature of a collection of types.
     */
    private static String createTypeCollectionId(Collection<? extends Type> types) {
        return types.stream()
            .sorted((t1, t2) -> createTypeId(t1).compareTo(createTypeId(t2)))
            .map(CdiSpiHelper::createTypeId)
            .collect(Collectors.joining(",", "[", "]"));
    }

    /**
     * Generates a unique signature for a {@link Type}.
     */
    private static String createTypeId(Type type) {
        if (type instanceof Class<?>)
            return Class.class.cast(type).getName();

        if (type instanceof ParameterizedType)
            return createTypeId(((ParameterizedType) type).getRawType())
                + Stream.of(((ParameterizedType) type).getActualTypeArguments())
                .map(CdiSpiHelper::createTypeId)
                .collect(Collectors.joining(",", "<", ">"));

        if (type instanceof TypeVariable<?>)
            return TypeVariable.class.cast(type).getName();

        if (type instanceof GenericArrayType)
            return createTypeId(GenericArrayType.class.cast(type).getGenericComponentType());

        throw new UnsupportedOperationException("Unable to create type id for type [" + type + "]");
    }

    /**
     * Generates a unique signature for a collection of annotations.
     */
    private static String createAnnotationCollectionId(Collection<Annotation> annotations) {
        if (annotations.isEmpty())
            return "";

        return annotations.stream()
            .sorted((a1, a2) -> a1.annotationType().getName().compareTo(a2.annotationType().getName()))
            .map(CdiSpiHelper::createAnnotationId)
            .collect(Collectors.joining(",", "[", "]"));
    }

    /**
     * Generates a unique signature for an {@link Annotation}.
     */
    private static String createAnnotationId(Annotation annotation) {
        Method[] methods = AccessController.doPrivileged(
            (PrivilegedAction<Method[]>) () -> annotation.annotationType().getDeclaredMethods());

        return Stream.of(methods)
            .sorted((m1, m2) -> m1.getName().compareTo(m2.getName()))
            .collect(() -> new StringJoiner(",", "@(", ")"),
                (joiner, method) -> {
                    try {
                        joiner
                            .add(method.getName()).add("=")
                            .add(method.invoke(annotation).toString());
                    } catch (NullPointerException | IllegalArgumentException | IllegalAccessException | InvocationTargetException cause) {
                        throw new RuntimeException(
                            "Error while accessing member [" + method.getName() + "]"
                            + " of annotation [" + annotation.annotationType().getName() + "]", cause);
                    }
                },
                StringJoiner::merge)
            .toString();
    }
}
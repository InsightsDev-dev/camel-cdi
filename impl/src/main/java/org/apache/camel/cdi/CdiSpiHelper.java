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
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanAttributes;
import javax.enterprise.util.Nonbinding;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.security.AccessController.doPrivileged;
import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

@Vetoed
final class CdiSpiHelper {

    static Predicate<Bean> hasType(Type type) {
        requireNonNull(type);
        return bean -> bean.getTypes().contains(type);
    }

    static Predicate<Annotation> isAnnotationType(Class<? extends Annotation> clazz) {
        requireNonNull(clazz);
        return annotation -> clazz.equals(annotation.annotationType());
    }

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
            .filter(Objects::nonNull)
            .collect(joining(","));
    }

    /**
     * Generates a unique signature of a collection of types.
     */
    private static String createTypeCollectionId(Collection<Type> types) {
        return types.stream()
            .sorted(comparing(CdiSpiHelper::createTypeId))
            .map(CdiSpiHelper::createTypeId)
            .collect(joining(",", "[", "]"));
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
                .collect(joining(",", "<", ">"));

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
            .collect(joining(",", "[", "]"));
    }

    /**
     * Generates a unique signature for an {@link Annotation}.
     */
    static String createAnnotationId(Annotation annotation) {
        Method[] methods = doPrivileged(
            (PrivilegedAction<Method[]>) () -> annotation.annotationType().getDeclaredMethods());

        return Stream.of(methods)
            .filter(method -> !method.isAnnotationPresent(Nonbinding.class))
            .sorted(comparing(Method::getName))
            .collect(() -> new StringJoiner(",", "@" + annotation.annotationType().getCanonicalName() + "(", ")"),
                (joiner, method) -> {
                    try {
                        joiner.add(method.getName() + "=" + method.invoke(annotation).toString());
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
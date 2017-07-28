/*
 * Copyright 2017 original authors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package org.particleframework.bind.annotation;

import org.particleframework.core.convert.ConversionContext;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.convert.ConvertibleMultiValues;
import org.particleframework.core.convert.ConvertibleValues;
import org.particleframework.core.naming.NameUtils;
import org.particleframework.inject.Argument;

import java.lang.annotation.Annotation;
import java.lang.reflect.TypeVariable;
import java.util.*;
import java.util.stream.Stream;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class AbstractAnnotatedArgumentBinder <A extends Annotation, T, S> implements AnnotatedArgumentBinder<A, T, S> {

    private final ConversionService<?> conversionService;

    protected AbstractAnnotatedArgumentBinder(ConversionService<?> conversionService) {
        this.conversionService = conversionService;
    }

    protected Optional<T> doBind(Argument<T> argument, ConvertibleValues<?> values, String annotationValue, Locale locale) {
        Class<T> argumentType = argument.getType();
        Object value = resolveValue(argument, values, argumentType, annotationValue);
        Format formatAnn = argument.findAnnotation(Format.class);
        if(value == null) {
            String fallbackName = getFallbackFormat(argument);
            if(!annotationValue.equals(fallbackName)) {

                annotationValue = fallbackName;
                value = resolveValue(argument, values, argumentType, annotationValue);
                if(value == null) {
                    return Optional.empty();
                }
            }
        }

        Class[] genericTypes = argument.getGenericTypes();
        Map<String,Class> typeParameterMap = null;

        if(genericTypes.length > 0) {
            TypeVariable<Class<T>>[] typeParameters = argumentType.getTypeParameters();
            if(typeParameters != null && typeParameters.length == genericTypes.length) {
                typeParameterMap = new LinkedHashMap<>();
                for (int i = 0; i < typeParameters.length; i++) {
                    TypeVariable<Class<T>> typeParameter = typeParameters[i];
                    Class genericType = genericTypes[i];
                    typeParameterMap.put(typeParameter.getName(), genericType);
                }
            }
        }

        ConversionContext conversionContext;
        if(formatAnn != null) {
            if(typeParameterMap != null) {
                conversionContext = ConversionContext.of(typeParameterMap, formatAnn.value(), locale);
            }
            else{
                conversionContext = ConversionContext.of(formatAnn.value(), locale);
            }
        }
        else {
            if(typeParameterMap != null) {
                conversionContext = ConversionContext.of(typeParameterMap);
            }
            else{
                conversionContext = ConversionContext.DEFAULT;
            }
        }
        return doConvert(value, argumentType, conversionContext);
    }

    private Object resolveValue(Argument<T> argument, ConvertibleValues<?> values, Class<T> argumentType, String annotationValue) {
        if(annotationValue.length() == 0) {
            annotationValue = argument.getName();
        }
        Object value = values.get(annotationValue, Object.class).orElse(null);
        if(values instanceof ConvertibleMultiValues && isManyObjects(argumentType)) {
            ConvertibleMultiValues<?> multiValues = (ConvertibleMultiValues<?>) values;
            List<?> all = multiValues.getAll(annotationValue);
            if(all != null && all.isEmpty()) {
                return null;
            }
            value = all;

        }
        else if(Map.class.isAssignableFrom(argumentType)) {
            value = values;
        }
        return value;
    }

    private boolean isManyObjects(Class<T> argumentType) {
        return argumentType.isArray() || Iterable.class.isAssignableFrom(argumentType) || Stream.class.isAssignableFrom(argumentType);
    }

    private Optional<T> doConvert(Object value, Class<T> targetType, ConversionContext context) {
        Optional<T> result = conversionService.convert(value, targetType, context);
        if(targetType == Optional.class && result.isPresent() ) {
            return (Optional<T>)result.get();
        }
        else {
            return result;
        }
    }

    protected String getFallbackFormat(Argument argument) {
        return NameUtils.hyphenate(argument.getName());
    }
}

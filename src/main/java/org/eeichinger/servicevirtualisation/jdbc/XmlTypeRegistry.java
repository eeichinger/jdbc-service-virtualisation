package org.eeichinger.servicevirtualisation.jdbc;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class XmlTypeRegistry {

    private static List<XmlTypeInfo<?>> DEFAULT_TYPES = new ArrayList<>();
    static {
        DEFAULT_TYPES.add(new XmlTypeInfo<>(BigDecimal.class, "xs:decimal", BigDecimal::new));
        DEFAULT_TYPES.add(new XmlTypeInfo<>(Integer.class, "xs:integer", Integer::new));
        DEFAULT_TYPES.add(new XmlTypeInfo<>(Long.class, "xs:long", Long::new));
    }

    @Getter
    @Setter
    private List<XmlTypeInfo<?>> xmlTypeInfoList = DEFAULT_TYPES;

    public String getXmlType(Class<?> clazz) {
        return DEFAULT_TYPES.stream()
            .filter(xmlTypeInfo -> xmlTypeInfo.clazz.equals(clazz))
            .findAny()
            .map(xmlTypeInfo -> xmlTypeInfo.xmlType)
            .orElse(null);
    }

    public Object parseValue(String xmlType, String val) {
        return DEFAULT_TYPES.stream()
            .filter(xmlTypeInfo -> xmlTypeInfo.xmlType.equals(xmlType))
            .findAny()
            .<Object>map(xmlTypeInfo -> xmlTypeInfo.mapper.apply(val))
            .orElse(val);
    }

    @Data
    @AllArgsConstructor
    public static class XmlTypeInfo<T> {
        Class<T> clazz;
        String xmlType;
        Function<String, T> mapper;
    }
}

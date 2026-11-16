package com.hospital.crm.system.role;

import com.hospital.crm.system.security.DataScopeType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class DataScopeTypeConverter implements AttributeConverter<DataScopeType, String> {

    @Override
    public String convertToDatabaseColumn(DataScopeType attribute) {
        return attribute == null ? null : attribute.code();
    }

    @Override
    public DataScopeType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : DataScopeType.fromCode(dbData);
    }
}

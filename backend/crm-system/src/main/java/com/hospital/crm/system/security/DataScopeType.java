package com.hospital.crm.system.security;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.exception.BusinessException;

/**
 * 角色数据范围。
 */
public enum DataScopeType {

    ALL("all"),
    ORG_AND_CHILDREN("org_and_children"),
    ORG("org"),
    SELF("self"),
    CUSTOM("custom");

    private final String code;

    DataScopeType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static DataScopeType fromCode(String code) {
        for (DataScopeType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "数据范围不合法: " + code);
    }
}

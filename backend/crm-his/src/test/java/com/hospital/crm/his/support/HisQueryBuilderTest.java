package com.hospital.crm.his.support;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HisQueryBuilderTest {

    private final HisQueryBuilder builder = new HisQueryBuilder();

    @Test
    void buildsParameterizedMobileSearchWithConfiguredView() {
        HisProperties properties = new HisProperties();
        HisQueryBuilder.SqlQuery query = builder.buildSearch(properties, "mobile", "138-0000-1111", 20);

        assertTrue(query.sql().contains("top (:limit)"));
        assertTrue(query.sql().contains("from [dbo].[v_crm_patient] p"));
        assertTrue(query.sql().contains("p.[mobile] = :value"));
        assertEquals("13800001111", query.parameters().get("value"));
        assertEquals(20, ((Number) query.parameters().get("limit")).intValue());
        assertEquals("mobile", query.matchType());
    }

    @Test
    void infersIdentityTypeAndEscapesFuzzyKeyword() {
        HisProperties properties = new HisProperties();

        assertEquals("mobile", builder.resolveIdentityType(null, "13800001111"));
        assertEquals("id_no", builder.resolveIdentityType(null, "110101199001011234"));
        assertEquals("any", builder.resolveIdentityType(null, "张三"));

        HisQueryBuilder.SqlQuery query = builder.buildSearch(properties, null, "张%三", 5);
        assertEquals("%张\\%三%", query.parameters().get("keyword"));
        assertTrue(query.sql().contains("p.[patient_name] like :keyword"));
    }

    @Test
    void rejectsUnknownIdentityType() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> builder.buildSearch(new HisProperties(), "passport", "123456", 20));
        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
    }

    @Test
    void rejectsIllegalViewOrColumnConfiguration() {
        HisProperties illegalView = new HisProperties();
        illegalView.getPatientView().setTable("dbo.v_crm_patient; drop table crm_customer");
        assertThrows(BusinessException.class,
                () -> builder.buildSearch(illegalView, "his_patient_id", "P001", 20));

        HisProperties illegalColumn = new HisProperties();
        illegalColumn.getPatientView().setMobileColumn("mobile] union select 1 --");
        assertThrows(BusinessException.class,
                () -> builder.buildSearch(illegalColumn, "mobile", "13800001111", 20));

        HisProperties missingColumn = new HisProperties();
        missingColumn.getPatientView().setMobileColumn(" ");
        assertThrows(BusinessException.class,
                () -> builder.buildSearch(missingColumn, "mobile", "13800001111", 20));
    }

    @Test
    void clampsLimitAndBuildsDetailQueries() {
        HisProperties properties = new HisProperties();

        assertEquals(100, ((Number) builder.buildSearch(properties, "his_patient_id", "P001", 500)
                .parameters().get("limit")).intValue());
        assertEquals(20, ((Number) builder.buildSearch(properties, "his_patient_id", "P001", 0)
                .parameters().get("limit")).intValue());

        HisQueryBuilder.SqlQuery visits = builder.buildVisits(properties, "P001", 20);
        assertTrue(visits.sql().contains("from [dbo].[v_crm_visit] v"));
        assertTrue(visits.sql().contains("order by v.[visit_time] desc"));

        HisQueryBuilder.SqlQuery admissions = builder.buildAdmissions(properties, "P001", 20);
        assertTrue(admissions.sql().contains("from [dbo].[v_crm_admission] a"));
    }
}

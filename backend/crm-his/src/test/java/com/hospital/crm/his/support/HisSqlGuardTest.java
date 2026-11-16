package com.hospital.crm.his.support;

import com.hospital.crm.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HisSqlGuardTest {

    private static final Set<String> ALLOWED = Set.of("dbo.v_crm_patient", "dbo.v_crm_visit");

    @Test
    void acceptsSelectOnWhitelistedViews() {
        assertDoesNotThrow(() -> HisSqlGuard.validateSelect(
                "select top (:limit) p.[patient_id] from [dbo].[v_crm_patient] p "
                        + "left join [dbo].[v_crm_visit] v on v.[patient_id] = p.[patient_id] "
                        + "where p.[mobile] = :value",
                ALLOWED));
    }

    @Test
    void rejectsWriteStatements() {
        assertThrows(BusinessException.class, () -> HisSqlGuard.validateSelect(
                "update [dbo].[v_crm_patient] set [mobile] = 'x'", ALLOWED));
        assertThrows(BusinessException.class, () -> HisSqlGuard.validateSelect(
                "select * from [dbo].[v_crm_patient]; delete from [dbo].[v_crm_patient]", ALLOWED));
        assertThrows(BusinessException.class, () -> HisSqlGuard.validateSelect(
                "select * into [dbo].[tmp] from [dbo].[v_crm_patient]", ALLOWED));
    }

    @Test
    void rejectsCommentsAndDangerousKeywords() {
        assertThrows(BusinessException.class, () -> HisSqlGuard.validateSelect(
                "select * from [dbo].[v_crm_patient] -- comment", ALLOWED));
        assertThrows(BusinessException.class, () -> HisSqlGuard.validateSelect(
                "select * from [dbo].[v_crm_patient] /* comment */", ALLOWED));
        assertThrows(BusinessException.class, () -> HisSqlGuard.validateSelect(
                "select * from [dbo].[v_crm_patient] union select * from [dbo].[v_crm_visit]", ALLOWED));
        assertThrows(BusinessException.class, () -> HisSqlGuard.validateSelect(
                "select * from [dbo].[v_crm_patient] where 1 = 1; exec xp_cmdshell 'dir'", ALLOWED));
    }

    @Test
    void rejectsViewOutsideWhitelist() {
        assertThrows(BusinessException.class, () -> HisSqlGuard.validateSelect(
                "select * from [dbo].[v_crm_payment]", ALLOWED));
        assertThrows(BusinessException.class, () -> HisSqlGuard.validateSelect(
                "select * from [dbo].[v_crm_patient] p join [other_db].[dbo].[v_crm_visit] v on 1 = 1", ALLOWED));
    }

    @Test
    void rejectsStatementWithoutTable() {
        assertThrows(BusinessException.class, () -> HisSqlGuard.validateSelect(
                "select 1", ALLOWED));
        assertThrows(BusinessException.class, () -> HisSqlGuard.validateSelect(
                "select * from [dbo].[v_crm_patient]", Set.of()));
    }

    @Test
    void rejectsIllegalIdentifiers() {
        assertThrows(BusinessException.class, () -> HisSqlGuard.requireIdentifier("patient_id; drop", "字段名"));
        assertThrows(BusinessException.class, () -> HisSqlGuard.requireIdentifier("patient-id", "字段名"));
        assertThrows(BusinessException.class, () -> HisSqlGuard.requireIdentifier(" ", "字段名"));
        assertDoesNotThrow(() -> HisSqlGuard.requireIdentifier("dbo.v_crm_patient", "视图"));
    }
}

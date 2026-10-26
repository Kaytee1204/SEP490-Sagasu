package com.example.base.dto.account;

import java.util.ArrayList;
import java.util.List;

public class ExcelImportResponse {

    private int totalRows;
    private int successCount;
    private int failedCount;
    private List<AccountDto> importedAccounts = new ArrayList<>();
    private List<String> errors = new ArrayList<>();

    public ExcelImportResponse() {
    }

    public int getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(int totalRows) {
        this.totalRows = totalRows;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(int failedCount) {
        this.failedCount = failedCount;
    }

    public List<AccountDto> getImportedAccounts() {
        return importedAccounts;
    }

    public void setImportedAccounts(List<AccountDto> importedAccounts) {
        this.importedAccounts = importedAccounts;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }
}

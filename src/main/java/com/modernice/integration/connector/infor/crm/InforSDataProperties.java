package com.modernice.integration.connector.infor.crm;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "integration.infor.sdata")
public class InforSDataProperties {

    private String baseUrl;
    private Auth auth = new Auth();
    private Fields fields = new Fields();

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Auth getAuth() {
        return auth;
    }

    public void setAuth(Auth auth) {
        this.auth = auth;
    }

    public Fields getFields() {
        return fields;
    }

    public void setFields(Fields fields) {
        this.fields = fields;
    }

    public static class Auth {
        private String type = "none";

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }

    public static class Fields {
        private String contactCustomerId = "MiCustomerId";
        private String contactErpTerritory = "ErpTerritory";
        private String contactErpContactNumber = "ErpExtId";
        private String contactHubSpotId = "HubSpotRecordId";
        private String accountCustomerId = "ExternalAccountNumber";
        private String accountHubSpotId = "HubSpotCompanyId";

        public String getContactCustomerId() {
            return contactCustomerId;
        }

        public void setContactCustomerId(String contactCustomerId) {
            this.contactCustomerId = contactCustomerId;
        }

        public String getContactErpTerritory() {
            return contactErpTerritory;
        }

        public void setContactErpTerritory(String contactErpTerritory) {
            this.contactErpTerritory = contactErpTerritory;
        }

        public String getContactErpContactNumber() {
            return contactErpContactNumber;
        }

        public void setContactErpContactNumber(String contactErpContactNumber) {
            this.contactErpContactNumber = contactErpContactNumber;
        }

        public String getContactHubSpotId() {
            return contactHubSpotId;
        }

        public void setContactHubSpotId(String contactHubSpotId) {
            this.contactHubSpotId = contactHubSpotId;
        }

        public String getAccountCustomerId() {
            return accountCustomerId;
        }

        public void setAccountCustomerId(String accountCustomerId) {
            this.accountCustomerId = accountCustomerId;
        }

        public String getAccountHubSpotId() {
            return accountHubSpotId;
        }

        public void setAccountHubSpotId(String accountHubSpotId) {
            this.accountHubSpotId = accountHubSpotId;
        }
    }
}

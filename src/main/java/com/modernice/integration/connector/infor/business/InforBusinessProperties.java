package com.modernice.integration.connector.infor.business;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "integration.infor.business")
public class InforBusinessProperties {
    private String crmBaseUrl = "";
    private String erpBaseUrl = "";
    private String dataLakeBaseUrl = "";
    private String authType = "none";
    private String accessToken = "";

    public String getCrmBaseUrl() { return crmBaseUrl; }
    public void setCrmBaseUrl(String crmBaseUrl) { this.crmBaseUrl = crmBaseUrl; }
    public String getErpBaseUrl() { return erpBaseUrl; }
    public void setErpBaseUrl(String erpBaseUrl) { this.erpBaseUrl = erpBaseUrl; }
    public String getDataLakeBaseUrl() { return dataLakeBaseUrl; }
    public void setDataLakeBaseUrl(String dataLakeBaseUrl) { this.dataLakeBaseUrl = dataLakeBaseUrl; }
    public String getAuthType() { return authType; }
    public void setAuthType(String authType) { this.authType = authType; }
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
}

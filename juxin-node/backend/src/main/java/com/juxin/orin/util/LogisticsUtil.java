package com.juxin.orin.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class LogisticsUtil {
    private static final Logger log = LoggerFactory.getLogger(LogisticsUtil.class);
    private static final String APP_CODE = "b7162e3dfc774517a95a715b8e97a1f4";
    private static final String LOGISTICS_API_URL = "https://wuliu.market.alicloudapi.com/kdi?no=";

    private final RestTemplate restTemplate = new RestTemplate();

    public Map<String, Object> queryLogistics(String trackingNumber) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "APPCODE " + APP_CODE);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    LOGISTICS_API_URL + trackingNumber,
                    HttpMethod.GET,
                    entity,
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            } else {
                log.error("Failed to query logistics. HTTP status: {}", response.getStatusCode());
                return null;
            }
        } catch (Exception e) {
            log.error("Exception querying logistics for tracking number: {}", trackingNumber, e);
            return null;
        }
    }
}

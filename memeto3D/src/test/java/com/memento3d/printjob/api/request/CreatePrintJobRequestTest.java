package com.memento3d.printjob.api.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CreatePrintJobRequestTest {

    @Test
    void acceptsInternalPrintJobPayloadAndIgnoresLegacyOutsourceFields() throws Exception {
        String payload = """
                {
                  "orderId": "cc8d39e4-3460-4abe-a5c9-60ffd1ce108e",
                  "materialId": "eef462de-739e-481e-ab4a-47a651298089",
                  "estimatedGram": 250,
                  "estimatedDurationSeconds": 14400,
                  "isOutsource": null,
                  "outsource": null,
                  "partnerName": null
                }
                """;

        CreatePrintJobRequest request = new ObjectMapper().readValue(payload, CreatePrintJobRequest.class);

        assertEquals("cc8d39e4-3460-4abe-a5c9-60ffd1ce108e", request.getOrderId().toString());
        assertEquals("eef462de-739e-481e-ab4a-47a651298089", request.getMaterialId().toString());
        assertEquals(0, request.getEstimatedGram().compareTo(new java.math.BigDecimal("250")));
        assertEquals(14400, request.getEstimatedDurationSeconds());
        assertNull(request.getPrinterId());
    }
}

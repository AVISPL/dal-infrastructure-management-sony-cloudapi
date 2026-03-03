/*
 * Copyright (c) 2023 AVI-SPL, Inc. All Rights Reserved.
 */
package com.avispl.symphony.dal.communicator;

import com.avispl.symphony.api.dal.dto.monitor.Statistics;
import com.avispl.symphony.api.dal.dto.monitor.aggregator.AggregatedDevice;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.util.List;

public class AggregatorCommunicatorTest {

    SonyCloudAPICommunicator communicator;

    @BeforeEach
    public void setUp() throws Exception {
        communicator = new SonyCloudAPICommunicator();
        communicator.setHost("api.apps.rdm.sony.net");
        communicator.setPort(443);
        communicator.setProtocol("https");
        communicator.setPassword("cZtsSDcV6BbKy2f4lpsyDDOzJlSKbXsMOExGFiQRM8ylDJJqh2mla49uxtQ1PvBfxzUqXEHubaBshIvJRLDy8zyUvhue6rArIj2EZSzEoHSw8F7xgBkBnvr7mYGHlUCy");
        communicator.setServiceProviderId("d47aeb95-f91c-4a47-962a-8cd5fa54d5d2");
        communicator.setWebhookPort(443);
        communicator.init();
    }

    @Test
    public void testGetMultupleStatistics() throws Exception {
        List<Statistics> statistics = communicator.getMultipleStatistics();
        Thread.sleep(300000);
        Assertions.assertNotNull(statistics);
    }

    @Test
    public void testRetrieveMultupleStatistics() throws Exception {
        List<AggregatedDevice> statistics = communicator.retrieveMultipleStatistics();
        Thread.sleep(30000);
        statistics = communicator.retrieveMultipleStatistics();
        Assertions.assertNotNull(statistics);
    }
}

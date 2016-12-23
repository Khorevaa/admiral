/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.test.upgrade;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.vmware.admiral.common.util.ServiceClientFactory;
import com.vmware.admiral.test.upgrade.version1.UpgradeOldHost;
import com.vmware.admiral.test.upgrade.version1.UpgradeOldService1.UpgradeOldService1State;
import com.vmware.admiral.test.upgrade.version1.UpgradeOldService2.UpgradeOldService2State;
import com.vmware.admiral.test.upgrade.version1.UpgradeOldService3.UpgradeOldService3State;
import com.vmware.admiral.test.upgrade.version2.UpgradeNewHost;
import com.vmware.admiral.test.upgrade.version2.UpgradeNewService1.UpgradeNewService1State;
import com.vmware.admiral.test.upgrade.version2.UpgradeNewService2.UpgradeNewService2State;
import com.vmware.admiral.test.upgrade.version2.UpgradeNewService3.UpgradeNewService3State;

public class ManagementHostUpgradeTest extends ManagementHostUpgradeBaseTest {

    private static final TemporaryFolder SANDBOX = new TemporaryFolder();

    private int hostPort;
    private String hostSandbox;

    @BeforeClass
    public static void beforeClass() {
        serviceClient = ServiceClientFactory.createServiceClient(null);
    }

    @AfterClass
    public static void afterClass() {
        serviceClient.stop();
    }

    @Before
    public void beforeTest() throws Exception {
        hostPort = 9292;

        SANDBOX.create();
        hostSandbox = SANDBOX.getRoot().toPath().toString();
    }

    @After
    public void afterTest() {
        if (upgradeHost != null) {
            stopHost(upgradeHost);
        }

        SANDBOX.delete();
    }

    @Test
    public void testService1AddNewFieldOptional() throws Throwable {

        upgradeHost = startHost(UpgradeOldHost.class, hostPort, hostSandbox);

        UpgradeOldService1State oldState = new UpgradeOldService1State();
        oldState.field1 = "foo";
        oldState.field2 = "bar";

        UpgradeOldService1State instance1 = createUpgradeServiceInstance(oldState);

        assertNotNull(instance1);
        assertEquals(oldState.field1, instance1.field1);
        assertEquals(oldState.field2, instance1.field2);

        stopHost(upgradeHost);

        /*
         * ---- Upgrade occurs here ----
         */

        upgradeHost = startHost(UpgradeNewHost.class, hostPort, hostSandbox);
        waitForServiceAvailability(upgradeHost, instance1.documentSelfLink);

        // get old instance

        UpgradeNewService1State instance2 = getUpgradeServiceInstance(instance1.documentSelfLink,
                UpgradeNewService1State.class);
        assertNotNull(instance2);
        assertEquals(oldState.field1, instance2.field1);
        assertEquals(oldState.field2, instance2.field2);
        assertEquals(null, instance2.field3);
        assertEquals(null, instance2.field4);
        assertEquals(null, instance2.field5);

        // update old instance

        instance2 = updateUpgradeServiceInstance(instance2);
        assertNotNull(instance2);
        assertEquals(oldState.field1, instance2.field1);
        assertEquals(oldState.field2, instance2.field2);
        assertEquals(null, instance2.field3);
        assertEquals(null, instance2.field4);
        assertEquals(null, instance2.field5);

        // CRU new instance

        UpgradeNewService1State newState = new UpgradeNewService1State();
        newState.field1 = "foo";
        newState.field2 = "bar";
        newState.field3 = "new";
        newState.field4 = 2015L;
        newState.field5 = Arrays.asList("a", "b", "c");

        instance2 = createUpgradeServiceInstance(newState);

        assertNotNull(instance2);
        assertEquals(newState.field1, instance2.field1);
        assertEquals(newState.field2, instance2.field2);
        assertEquals(newState.field3, instance2.field3);
        assertEquals(newState.field4, instance2.field4);
        assertEquals(newState.field5, instance2.field5);

        Collection<UpgradeNewService1State> instances;
        instances = queryUpgradeServiceInstances(UpgradeNewService1State.class, "field3", "new");
        assertEquals(1, instances.size());

        stopHost(upgradeHost);
    }

    @Test
    public void testService2AddNewFieldRequired() throws Throwable {

        upgradeHost = startHost(UpgradeOldHost.class, hostPort, hostSandbox);

        UpgradeOldService2State oldState = new UpgradeOldService2State();
        oldState.field1 = "foo";
        oldState.field2 = "bar";

        UpgradeOldService2State instance1 = createUpgradeServiceInstance(oldState);

        assertNotNull(instance1);
        assertEquals(oldState.field1, instance1.field1);
        assertEquals(oldState.field2, instance1.field2);

        stopHost(upgradeHost);

        /*
         * ---- Upgrade occurs here ----
         */

        upgradeHost = startHost(UpgradeNewHost.class, hostPort, hostSandbox);
        waitForServiceAvailability(upgradeHost, instance1.documentSelfLink);

        // get old instance

        UpgradeNewService2State instance2 = getUpgradeServiceInstance(instance1.documentSelfLink,
                UpgradeNewService2State.class);
        assertNotNull(instance2);
        assertEquals(oldState.field1, instance2.field1);
        assertEquals(oldState.field2, instance2.field2);
        assertEquals("default value", instance2.field3);
        assertEquals(Long.valueOf(42), instance2.field4);
        assertEquals(Arrays.asList("a", "b", "c"), instance2.field5);

        Collection<UpgradeNewService2State> instances;
        instances = queryUpgradeServiceInstances(UpgradeNewService2State.class, "field3",
                "default value");
        // The query does not work despite of the right value when doing a GET!
        // After an update (PUT) the query works tough.
        // assertEquals(1, instances.size());

        // update old instance

        instance2.field3 += " updated";

        instance2 = updateUpgradeServiceInstance(instance2);
        assertNotNull(instance2);
        assertEquals(oldState.field1, instance2.field1);
        assertEquals(oldState.field2, instance2.field2);
        assertEquals("default value updated", instance2.field3);
        assertEquals(Long.valueOf(42), instance2.field4);
        assertEquals(Arrays.asList("a", "b", "c"), instance2.field5);

        instances = queryUpgradeServiceInstances(UpgradeNewService2State.class, "field3",
                "default value");
        assertEquals(0, instances.size());
        instances = queryUpgradeServiceInstances(UpgradeNewService2State.class, "field3",
                "default value updated");
        assertEquals(1, instances.size());

        // CRU new instance

        UpgradeNewService2State newState = new UpgradeNewService2State();
        newState.field1 = "foo";
        newState.field2 = "bar";
        newState.field3 = "new";
        newState.field4 = 2015L;
        newState.field5 = Arrays.asList("a", "b", "c");
        instance2 = createUpgradeServiceInstance(newState);

        assertNotNull(instance2);
        assertEquals(newState.field1, instance2.field1);
        assertEquals(newState.field2, instance2.field2);
        assertEquals(newState.field3, instance2.field3);
        assertEquals(newState.field4, instance2.field4);
        assertEquals(newState.field5, instance2.field5);

        instances = queryUpgradeServiceInstances(UpgradeNewService2State.class, "field3", "new");
        assertEquals(1, instances.size());

        stopHost(upgradeHost);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testService3ChangeFieldType() throws Throwable {

        upgradeHost = startHost(UpgradeOldHost.class, hostPort, hostSandbox);

        UpgradeOldService3State oldState = new UpgradeOldService3State();
        oldState.field1 = "foo";
        oldState.field2 = "bar";
        oldState.field3 = "fortytwo";
        oldState.field4 = Arrays.asList("a", "b", "c");
        oldState.field5 = Arrays.asList("a", "b", "c");
        oldState.field6 = new HashMap<>();
        oldState.field6.put("one", "1");
        oldState.field6.put("two", "2");

        UpgradeOldService3State instance1 = createUpgradeServiceInstance(oldState);

        assertNotNull(instance1);
        assertEquals(oldState.field1, instance1.field1);
        assertEquals(oldState.field2, instance1.field2);
        assertEquals(oldState.field3, instance1.field3);
        assertEquals(oldState.field4, instance1.field4);
        assertEquals(oldState.field5, instance1.field5);
        assertEquals(oldState.field6, instance1.field6);

        stopHost(upgradeHost);

        /*
         * ---- Upgrade occurs here ----
         */

        upgradeHost = startHost(UpgradeNewHost.class, hostPort, hostSandbox);
        waitForServiceAvailability(upgradeHost, instance1.documentSelfLink);

        // get old instance

        UpgradeNewService3State instance2 = getUpgradeServiceInstance(instance1.documentSelfLink,
                UpgradeNewService3State.class);
        assertNotNull(instance2);
        assertEquals(oldState.field1, instance2.field1);
        assertEquals(oldState.field2, instance2.field2);
        assertEquals(Long.valueOf(42), instance2.field3);
        HashSet<String> expectedField4 = new HashSet<>(Arrays.asList("a", "b", "c"));
        assertEquals(expectedField4, instance2.field4);
        Map<String, String> expectedField5 = new HashMap<>();
        expectedField5.put("a", "a");
        expectedField5.put("b", "b");
        expectedField5.put("c", "c");
        assertEquals(expectedField5, instance2.field5);
        Map<String, String> expectedField6 = new HashMap<>();
        expectedField6.put("one", "1");
        expectedField6.put("two", "2");
        assertEquals(expectedField6, instance2.field6);

        // TODO - Adapt queries to non-String fields also...
        // Collection<UpgradeNewService3State> instances;
        // instances = queryUpgradeServiceInstances(UpgradeNewService3State.class, "field3",
        // "default value");
        // assertEquals(1, instances.size());

        // update old instance

        instance2.field3 *= 2;

        instance2 = updateUpgradeServiceInstance(instance2);
        assertNotNull(instance2);
        assertEquals(oldState.field1, instance2.field1);
        assertEquals(oldState.field2, instance2.field2);
        assertEquals(Long.valueOf(42 * 2), instance2.field3);
        assertEquals(expectedField4, instance2.field4);
        assertEquals(expectedField5, instance2.field5);
        assertEquals(expectedField6, instance2.field6);

        // instances = queryUpgradeServiceInstances(UpgradeNewService3State.class, "field3",
        // "42");
        // assertEquals(0, instances.size());
        // instances = queryUpgradeServiceInstances(UpgradeNewService3State.class, "field3",
        // "84");
        // // assertEquals(1, instances.size());

        // CRU new instance

        UpgradeNewService3State newState = new UpgradeNewService3State();
        newState.field1 = "foo";
        newState.field2 = "bar";
        newState.field3 = 2015L;
        newState.field4 = new HashSet<>(Arrays.asList("a", "b", "c"));
        newState.field5 = new HashMap<>();
        newState.field5.put("a", "a");
        newState.field5.put("b", "b");
        newState.field5.put("c", "c");
        newState.field6 = new HashMap<>();
        newState.field6.put("one", "1");
        newState.field6.put("two", "2");

        instance2 = createUpgradeServiceInstance(newState);

        assertNotNull(instance2);
        assertEquals(newState.field1, instance2.field1);
        assertEquals(newState.field2, instance2.field2);
        assertEquals(newState.field3, instance2.field3);
        assertEquals(newState.field4, instance2.field4);
        assertEquals(newState.field5, instance2.field5);
        assertEquals(newState.field6, instance2.field6);

        // instances = queryUpgradeServiceInstances(UpgradeNewService3State.class, "field3", "new");
        // assertEquals(1, instances.size());

        stopHost(upgradeHost);
    }

}

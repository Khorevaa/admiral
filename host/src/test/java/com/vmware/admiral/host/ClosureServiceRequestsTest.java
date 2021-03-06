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

package com.vmware.admiral.host;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.google.gson.JsonPrimitive;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.closures.services.closure.Closure;
import com.vmware.admiral.closures.services.closure.ClosureFactoryService;
import com.vmware.admiral.closures.services.closuredescription.ClosureDescription;
import com.vmware.admiral.closures.services.closuredescription.ClosureDescriptionFactoryService;
import com.vmware.admiral.closures.services.closuredescription.ResourceConstraints;
import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.common.test.HostInitTestDcpServicesConfig;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.container.GroupResourcePlacementService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationProcessingChain;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;

public class ClosureServiceRequestsTest extends BaseTestCase {

    private static final int DEFAULT_OPERATION_TIMEOUT = 30;

    private ComputeService.ComputeState compute;

    @Before
    public void setUp() throws Exception {
        try {
            startServices(this.host);

            waitForServiceAvailability(ComputeInitialBootService.SELF_LINK);
            waitForInitialBootServiceToBeSelfStopped(ComputeInitialBootService.SELF_LINK);
            compute = createTestHostState();

        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @After
    public void after() throws Throwable {
        clean(UriUtils.buildUri(this.host, compute.documentSelfLink));
    }

    @Override
    protected boolean getPeerSynchronizationEnabled() {
        return true;
    }

    @Override
    protected void customizeChains(
            Map<Class<? extends Service>, Class<? extends OperationProcessingChain>> chains) {
        CompositeComponentNotificationProcessingChain.registerOperationProcessingChains(chains);
    }

    @Test
    public void executeJSNumberParametersTest() throws Throwable {
        // Create Closure Definition
        URI factoryUri = UriUtils
                .buildFactoryUri(this.host, ClosureDescriptionFactoryService.class);
        TestContext ctx = testCreate(1);

        ClosureDescription closureDefState = new ClosureDescription();
        closureDefState.name = "test";

        int expectedInVar = 3;
        int expectedOutVar = 3;
        double expectedResult = 3;

        closureDefState.source =
                "function test(x) {print('Hello number: ' + x); return x + 1;} var b = " +
                        expectedOutVar
                        + "; result = test(inputs.a);";
        closureDefState.runtime = "nodejs";
        closureDefState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        closureDefState.documentSelfLink = UUID.randomUUID().toString();
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 10;
        closureDefState.resources = constraints;
        ClosureDescription[] responses = new ClosureDescription[1];
        URI closureDefChildURI = UriUtils.buildUri(this.host,
                ClosureDescriptionFactoryService.FACTORY_LINK + "/"
                        + closureDefState.documentSelfLink);
        Operation post = Operation
                .createPost(factoryUri)
                .setBody(closureDefState)
                .setCompletion((o, e) -> {
                    assertNull(e);
                    responses[0] = o.getBody(ClosureDescription.class);
                    assertNotNull(responses[0]);
                    ctx.completeIteration();
                });
        this.host.send(post);
        ctx.await();

        // Create Closure
        TestContext ctx1 = testCreate(1);
        URI factoryTaskUri = UriUtils.buildFactoryUri(this.host, ClosureFactoryService.class);
        //        this.host.testStart(1);
        Closure closureState = new Closure();
        closureState.descriptionLink =
                ClosureDescriptionFactoryService.FACTORY_LINK + "/"
                        + closureDefState.documentSelfLink;
        closureState.documentSelfLink = UUID.randomUUID().toString();
        URI closureChildURI = UriUtils.buildUri(this.host,
                ClosureFactoryService.FACTORY_LINK + "/" + closureState.documentSelfLink);
        final Closure[] closureResponses = new Closure[1];
        Operation closurePost = Operation
                .createPost(factoryTaskUri)
                .setBody(closureState)
                .setCompletion((o, e) -> {
                    closureResponses[0] = o.getBody(Closure.class);
                    assertEquals(closureState.descriptionLink, closureResponses[0].descriptionLink);
                    assertEquals(TaskState.TaskStage.CREATED, closureResponses[0].state);
                    ctx1.completeIteration();
                });
        this.host.send(closurePost);
        ctx1.await();

        // Executing the created Closure
        TestContext ctx2 = testCreate(1);
        Closure closureRequest = new Closure();
        Map inputs = new HashMap<>();
        inputs.put("a", new JsonPrimitive(expectedInVar));
        closureRequest.inputs = inputs;
        Operation closureExecPost = Operation
                .createPost(closureChildURI)
                .setBody(closureRequest)
                .setCompletion((o, e) -> {
                    closureResponses[0] = o.getBody(Closure.class);
                    assertNotNull(closureResponses[0]);
                    ctx2.completeIteration();
                });
        this.host.send(closureExecPost);
        ctx2.await();

        // Wait for the completion timeout
        waitForCompletion(closureState.documentSelfLink, DEFAULT_OPERATION_TIMEOUT);

        final Closure[] finalClosureResponse = new Closure[1];
        TestContext ctx3 = testCreate(1);
        Operation closureGet = Operation
                .createGet(closureChildURI)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        ctx3.failIteration(e);
                    } else {
                        try {
                            finalClosureResponse[0] = o.getBody(Closure.class);
                            assertEquals(closureState.descriptionLink,
                                    finalClosureResponse[0].descriptionLink);
                            assertEquals(TaskState.TaskStage.FINISHED,
                                    finalClosureResponse[0].state);

                            assertEquals(expectedInVar,
                                    finalClosureResponse[0].inputs.get("a").getAsInt());
                            assertEquals(expectedResult,
                                    finalClosureResponse[0].outputs.get("a").getAsInt(), 0);
                            ctx3.completeIteration();
                        } catch (Throwable ex) {
                            ctx3.failIteration(ex);
                        }
                    }
                });
        this.host.send(closureGet);
        ctx3.await();

        clean(closureChildURI);
        clean(closureDefChildURI);
    }

    private ComputeService.ComputeState createTestHostState() throws Throwable {
        ComputeService.ComputeState state = createComputeState();
        state.resourcePoolLink = GroupResourcePlacementService.DEFAULT_RESOURCE_POOL_LINK;
        state.powerState = ComputeService.PowerState.ON;
        state = doPost(state, ComputeService.FACTORY_LINK);
        return state;
    }

    private ComputeService.ComputeState createComputeState() {
        ComputeService.ComputeState computeState = new ComputeService.ComputeState();
        computeState.descriptionLink =
                ComputeService.FACTORY_LINK + "/" + UUID.randomUUID().toString();
        computeState.address = "https://test-server";
        computeState.customProperties = new HashMap<>();
        computeState.customProperties.put(ComputeConstants.HOST_AUTH_CREDENTIALS_PROP_NAME,
                "authCredentialsLink");
        computeState.customProperties.put(ContainerHostService.HOST_DOCKER_ADAPTER_TYPE_PROP_NAME,
                ContainerHostService.DockerAdapterType.API.name());

        return computeState;
    }

    private static void startServices(ServiceHost serviceHost) throws Throwable {
        DeploymentProfileConfig.getInstance().setTest(true);
        HostInitPhotonModelServiceConfig.startServices(serviceHost);
        HostInitTestDcpServicesConfig.startServices(serviceHost);
        HostInitCommonServiceConfig.startServices(serviceHost);
        HostInitComputeServicesConfig.startServices(serviceHost, false);
        HostInitDockerAdapterServiceConfig.startServices(serviceHost, true);
        HostInitClosureServiceConfig.startServices(serviceHost, true);
    }

    private Closure getClosure(String closureLink) throws InterruptedException, ExecutionException,
            TimeoutException {

        CompletableFuture<Operation> c = new CompletableFuture<Operation>();

        TestContext ctx = testCreate(1);
        URI closureUri = UriUtils.buildUri(this.host,
                ClosureFactoryService.FACTORY_LINK + "/" + closureLink);
        Operation closureGet = Operation
                .createGet(closureUri)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        c.completeExceptionally(ex);
                        ctx.failIteration(ex);
                    } else {
                        c.complete(o);
                        ctx.completeIteration();
                    }

                });

        //        this.host.testStart(1);

        this.host.send(closureGet);
        ctx.await();

        return c.get(2000, TimeUnit.MILLISECONDS).getBody(Closure.class);
    }

    private void clean(URI childURI) throws Throwable {
        TestContext ctx = testCreate(1);
        CompletableFuture<Operation> c = new CompletableFuture<Operation>();
        Operation delete = Operation
                .createDelete(childURI)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        c.completeExceptionally(ex);
                        ctx.failIteration(ex);
                    } else {
                        c.complete(o);
                        ctx.completeIteration();
                    }
                });

        this.host.send(delete);
        ctx.await();

        c.get(5000, TimeUnit.MILLISECONDS);
    }

    private void waitForCompletion(String closureLink, int timeout)
            throws Exception {
        Closure fetchedClosure = getClosure(closureLink);
        long startTime = System.currentTimeMillis();
        while (!isCompleted(fetchedClosure) && !isTimeoutElapsed(startTime, timeout)) {
            try {
                Thread.sleep(500);
                fetchedClosure = getClosure(closureLink);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean isCompleted(Closure fetchedClosure) {
        return TaskState.TaskStage.CREATED != fetchedClosure.state
                && TaskState.TaskStage.STARTED != fetchedClosure.state;
    }

    private boolean isTimeoutElapsed(long startTime, int timeout) {
        return System.currentTimeMillis() - startTime > TimeUnit.SECONDS.toMillis(timeout);
    }

}

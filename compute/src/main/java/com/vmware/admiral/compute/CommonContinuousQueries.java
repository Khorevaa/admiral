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

package com.vmware.admiral.compute;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;

import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.ServiceSubscriptionState.ServiceSubscriber;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Helper class allowing sharing continuous queries between multiple components for performance
 * reasons.
 */
public class CommonContinuousQueries {
    /**
     * Unique prefix for continuous query tasks. Changes upon restart which is OK because
     * continuous queries are not persisted.
     */
    private static final String QUERY_TASK_SELF_LINK_PREFIX = UUID.randomUUID().toString();

    /**
     * Supported common queries.
     */
    public static enum ContinuousQueryId {
        /**
         * Query for all {@link ComputeState}s.
         */
        COMPUTES
    }

    /**
     * Subscribes a consumer to the given continuous query.
     */
    public static void subscribeTo(ServiceHost host, ContinuousQueryId queryId,
            Consumer<Operation> consumer) {
        QueryTask task = getQueryTask(host, queryId);
        Operation.createPost(host, ServiceUriPaths.CORE_LOCAL_QUERY_TASKS)
                .setBody(task)
                .setReferer(host.getUri())
                .setCompletion((o, e) -> {
                    if (e != null && o.getStatusCode() != Operation.STATUS_CODE_CONFLICT) {
                        host.log(Level.SEVERE, Utils.toString(e));
                        return;
                    }

                    String taskUriPath = UriUtils.buildUriPath(
                            ServiceUriPaths.CORE_LOCAL_QUERY_TASKS, task.documentSelfLink);
                    Operation subscribePost = Operation.createPost(host, taskUriPath)
                            .setReferer(host.getUri())
                            .setCompletion((op, ex) -> {
                                if (ex != null) {
                                    host.log(Level.SEVERE, Utils.toString(ex));
                                }
                            });

                    host.log(Level.INFO, "Subscribing to a continuous task: %s", taskUriPath);
                    host.startSubscriptionService(subscribePost, consumer,
                            ServiceSubscriber.create(false));
                }).sendWith(host);
    }

    private static QueryTask getQueryTask(ServiceHost host, ContinuousQueryId queryId) {
        QueryTask task;

        switch (queryId) {
        case COMPUTES:
            Query query = Query.Builder.create()
                    .addKindFieldClause(ComputeState.class)
                    .addFieldClause(ServiceDocument.FIELD_NAME_OWNER, host.getId())
                    .build();
            task = QueryTask.Builder.create().addOption(QueryOption.CONTINUOUS)
                    .setQuery(query).build();
            break;
        default:
            throw new IllegalArgumentException("Unrecognized common query: " + queryId);
        }

        task.documentSelfLink = getTaskSelfLink(queryId);
        return task;
    }

    private static String getTaskSelfLink(ContinuousQueryId queryId) {
        return QUERY_TASK_SELF_LINK_PREFIX + "-" + queryId.name().toLowerCase();
    }
}

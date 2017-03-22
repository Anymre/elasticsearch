/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.elasticsearch.persistent;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.cluster.node.tasks.cancel.CancelTasksRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.component.AbstractComponent;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.tasks.Task;
import org.elasticsearch.tasks.TaskId;

/**
 * This service is used by persistent actions to propagate changes in the action state and notify about completion
 */
public class PersistentTasksService extends AbstractComponent {

    private final Client client;
    private final ClusterService clusterService;

    public PersistentTasksService(Settings settings, ClusterService clusterService, Client client) {
        super(settings);
        this.client = client;
        this.clusterService = clusterService;
    }

    /**
     * Creates the specified persistent action and tries to start it immediately, upon completion the task is
     * removed from the cluster state
     */
    public <Request extends PersistentTaskRequest> void createPersistentActionTask(String action, Request request,
                                                                                   PersistentTaskOperationListener listener) {
        createPersistentActionTask(action, request, false, true, listener);
    }

    /**
     * Creates the specified persistent action. The action is started unless the stopped parameter is equal to true.
     * If removeOnCompletion parameter is equal to true, the task is removed from the cluster state upon completion.
     * Otherwise it will remain there in the stopped state.
     */
    public <Request extends PersistentTaskRequest> void createPersistentActionTask(String action, Request request,
                                                                                   boolean stopped,
                                                                                   boolean removeOnCompletion,
                                                                                   PersistentTaskOperationListener listener) {
        CreatePersistentTaskAction.Request createPersistentActionRequest = new CreatePersistentTaskAction.Request(action, request);
        createPersistentActionRequest.setStopped(stopped);
        createPersistentActionRequest.setRemoveOnCompletion(removeOnCompletion);
        try {
            client.execute(CreatePersistentTaskAction.INSTANCE, createPersistentActionRequest, ActionListener.wrap(
                    o -> listener.onResponse(o.getTaskId()), listener::onFailure));
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }

    /**
     * Notifies the PersistentTasksClusterService about successful (failure == null) completion of a task or its failure
     *
     */
    public void sendCompletionNotification(long taskId, Exception failure, PersistentTaskOperationListener listener) {
        CompletionPersistentTaskAction.Request restartRequest = new CompletionPersistentTaskAction.Request(taskId, failure);
        try {
            client.execute(CompletionPersistentTaskAction.INSTANCE, restartRequest, ActionListener.wrap(o -> listener.onResponse(taskId),
                    listener::onFailure));
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }

    /**
     * Cancels the persistent task.
     */
    public void sendCancellation(long taskId, PersistentTaskOperationListener listener) {
        DiscoveryNode localNode = clusterService.localNode();
        CancelTasksRequest cancelTasksRequest = new CancelTasksRequest();
        cancelTasksRequest.setTaskId(new TaskId(localNode.getId(), taskId));
        cancelTasksRequest.setReason("persistent action was removed");
        try {
            client.admin().cluster().cancelTasks(cancelTasksRequest, ActionListener.wrap(o -> listener.onResponse(taskId),
                    listener::onFailure));
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }

    /**
     * Updates status of the persistent task
     */
    public void updateStatus(long taskId, Task.Status status, PersistentTaskOperationListener listener) {
        UpdatePersistentTaskStatusAction.Request updateStatusRequest = new UpdatePersistentTaskStatusAction.Request(taskId, status);
        try {
            client.execute(UpdatePersistentTaskStatusAction.INSTANCE, updateStatusRequest, ActionListener.wrap(
                    o -> listener.onResponse(taskId), listener::onFailure));
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }

    /**
     * Removes a persistent task
     */
    public void removeTask(long taskId, PersistentTaskOperationListener listener) {
        RemovePersistentTaskAction.Request removeRequest = new RemovePersistentTaskAction.Request(taskId);
        try {
            client.execute(RemovePersistentTaskAction.INSTANCE, removeRequest, ActionListener.wrap(o -> listener.onResponse(taskId),
                    listener::onFailure));
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }

    /**
     * Starts a persistent task
     */
    public void startTask(long taskId, PersistentTaskOperationListener listener) {
        StartPersistentTaskAction.Request startRequest = new StartPersistentTaskAction.Request(taskId);
        try {
            client.execute(StartPersistentTaskAction.INSTANCE, startRequest, ActionListener.wrap(o -> listener.onResponse(taskId),
                    listener::onFailure));
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }

    public interface PersistentTaskOperationListener {
        void onResponse(long taskId);
        void onFailure(Exception e);
    }

}

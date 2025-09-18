package com.amazon.tdm.s3a.service;

import com.amazon.tdm.s3a.model.AlreadyExistsException;
import com.amazon.tdm.s3a.model.CreateWorkflowRequest;
import com.amazon.tdm.s3a.model.DeleteWorkflowRequest;
import com.amazon.tdm.s3a.model.EchoInput;
import com.amazon.tdm.s3a.model.GetWorkflowRequest;
import com.amazon.tdm.s3a.model.InvalidInputException;
import com.amazon.tdm.s3a.model.SendControlCommandRequest;
import com.amazon.tdm.s3a.model.StartWorkflowRequest;
import com.amazon.tdm.s3a.service.activity.WorkflowActivity;
import com.amazon.tdm.s3a.service.dagger.DaggerLambdaComponent;
import com.amazon.tdm.s3a.service.dagger.LambdaComponent;
import com.amazon.tdm.s3a.service.utils.ErrorResponse;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class LambdaMain implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private static final Logger log = LogManager.getLogger(LambdaMain.class);
    private final LambdaComponent lambdaComponent;
    private final WorkflowActivity workflowActivity;
    private final Gson gson;
    private static final String ROUTE_CREATE_WORKFLOW = "/createWorkflow";
    private static final String ROUTE_GET_WORKFLOW = "/getWorkflow";
    private static final String ROUTE_START_WORKFLOW = "/startWorkflow";
    private static final String ROUTE_START_MANIFEST_SPLIT_WORKFLOW = "/startManifestSplitWorkflow";
    private static final String ROUTE_DELETE_WORKFLOW = "/deleteWorkflow";
    private static final String ROUTE_SEND_CONTROL_COMMAND = "/sendControlCommand";
    private static final String ROUTE_LIST_WORKFLOWS = "/listWorkflows";
    private static final String ROUTE_ECHO = "/Echo";
    private static final String HTTP_METHOD_POST = "POST";
    private static final int STATUS_CODE_500 = 500;
    private static final int STATUS_CODE_200 = 200;
    private static final int STATUS_CODE_404 = 404;

    public LambdaMain() {
        this.lambdaComponent = DaggerLambdaComponent.create();
        this.workflowActivity = lambdaComponent.getWorkflowActivity();
        this.gson = lambdaComponent.getGson();
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        final String path = request.getPath();
        final String httpMethod = request.getHttpMethod();
        final String requestId = context.getAwsRequestId();
        log.info("Processing request {} for path: {}, method: {}",
            requestId, request.getPath(), request.getHttpMethod());
        try {
            switch (path) {
                case ROUTE_CREATE_WORKFLOW:
                    if (HTTP_METHOD_POST.equals(httpMethod)) {
                        CreateWorkflowRequest createWorkflowRequest = gson.fromJson(request.getBody(), CreateWorkflowRequest.class);
                        return createApiGatewayResponse(() -> workflowActivity.createWorkflow(createWorkflowRequest), context);
                    }
                    break;
                case ROUTE_GET_WORKFLOW:
                    if (HTTP_METHOD_POST.equals(httpMethod)) {
                        GetWorkflowRequest getWorkflowRequest = gson.fromJson(request.getBody(), GetWorkflowRequest.class);
                        return createApiGatewayResponse(() -> workflowActivity.getWorkflow(getWorkflowRequest), context);
                    }
                    break;
                case ROUTE_START_WORKFLOW:
                    if (HTTP_METHOD_POST.equals(httpMethod)) {
                        StartWorkflowRequest startWorkflowRequest = gson.fromJson(request.getBody(), StartWorkflowRequest.class);
                        return createApiGatewayResponse(() -> workflowActivity.startWorkflow(startWorkflowRequest), context);
                    }
                    break;
                case ROUTE_START_MANIFEST_SPLIT_WORKFLOW:
                    if (HTTP_METHOD_POST.equals(httpMethod)) {
                        StartWorkflowRequest startWorkflowRequest = gson.fromJson(request.getBody(), StartWorkflowRequest.class);
                        return createApiGatewayResponse(() -> workflowActivity.startManifestSplitWorkflow(startWorkflowRequest), context);
                    }
                    break;
                case ROUTE_DELETE_WORKFLOW:
                    if (HTTP_METHOD_POST.equals(httpMethod)) {
                        DeleteWorkflowRequest deleteWorkflowRequest = gson.fromJson(request.getBody(), DeleteWorkflowRequest.class);
                        return createApiGatewayResponse(() -> workflowActivity.deleteWorkflow(deleteWorkflowRequest), context);
                    }
                    break;
                case ROUTE_SEND_CONTROL_COMMAND:
                    if (HTTP_METHOD_POST.equals(httpMethod)) {
                        SendControlCommandRequest sendControlCommandRequest = gson.fromJson(request.getBody(), SendControlCommandRequest.class);
                        return createApiGatewayResponse(() -> workflowActivity.sendControlCommand(sendControlCommandRequest), context);
                    }
                    break;
                case ROUTE_LIST_WORKFLOWS:
                    if (HTTP_METHOD_POST.equals(httpMethod)) {
                        return createApiGatewayResponse(() -> workflowActivity.listWorkflows(), context);
                    }
                    break;
                case ROUTE_ECHO:
                    if (HTTP_METHOD_POST.equals(httpMethod)) {
                        EchoInput echoInput = gson.fromJson(request.getBody(), EchoInput.class);
                        return createApiGatewayResponse(() -> workflowActivity.echo(echoInput), context);
                    }
                    break;
            }

        } catch (Exception e) {
            log.error("Unexpected error occurred", e);
            return createErrorResponse(STATUS_CODE_500,"Internal server error", requestId);
        }

            return new APIGatewayProxyResponseEvent()
                .withStatusCode(STATUS_CODE_404)
                .withBody("Route not found");
    }

    /**
     * Wraps the action in a try-catch block and handles exceptions.
     * @param action the action to be executed
     * @return the response event
     */
    private APIGatewayProxyResponseEvent createApiGatewayResponse(Supplier<Object> action, Context context) {
        try {
            Object result = action.get();
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            headers.put("Access-Control-Allow-Origin", "*");
            headers.put("Access-Control-Allow-Methods", "GET,POST,DELETE,OPTIONS");

            return new APIGatewayProxyResponseEvent()
                .withStatusCode(STATUS_CODE_200)
                .withHeaders(headers)
                .withBody(gson.toJson(result));
        } catch (InvalidInputException e) {
            log.error("Invalid input error", e);
            return createErrorResponse(400, e.getMessage(), context.getAwsRequestId());
        } catch (AlreadyExistsException e) {
            log.error("Resource already exists", e);
            return createErrorResponse(409, e.getMessage(), context.getAwsRequestId());
        } catch (Exception e) {
            log.error("Unexpected error in workflow operation", e);
            return createErrorResponse(500, e.getMessage(), context.getAwsRequestId());
        }
    }


    private APIGatewayProxyResponseEvent createErrorResponse(int statusCode, String message, String errorId) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");

        ErrorResponse errorResponse = new ErrorResponse(
            message,
            statusCode,
            errorId
        );

        return new APIGatewayProxyResponseEvent()
            .withStatusCode(statusCode)
            .withHeaders(headers)
            .withBody(gson.toJson(errorResponse));
    }
}
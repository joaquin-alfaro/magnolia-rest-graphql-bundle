package com.formentor.magnolia.rest.graphql.endpoint;

import com.formentor.magnolia.rest.graphql.service.GraphQLProvider;
import info.magnolia.rest.AbstractEndpoint;
import info.magnolia.rest.EndpointDefinition;
import info.magnolia.rest.delivery.jcr.i18n.I18n;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Api(value = "/magnolia-rest-graphql")
@Path("/magnolia-rest-graphql")
@I18n
@Slf4j
public class GraphQLEndpoint<D extends EndpointDefinition> extends AbstractEndpoint<D> {

    private static final String STATUS_MESSAGE_OK = "OK";
    private static final String STATUS_MESSAGE_METHOD_NOT_ALLOWED = "Method Not Allowed";
    private static final String STATUS_MESSAGE_NO_CONTENT = "No content and Not Found";
    private static final String STATUS_MESSAGE_NOT_FOUND = "Not Found";
    private static final String STATUS_MESSAGE_INTERNAL_ERROR = "Internal Server Error";

    private final GraphQLProvider graphQLService;
    @Inject
    public GraphQLEndpoint(D endpointDefinition, GraphQLProvider graphQLProvider) {
        super(endpointDefinition);
        this.graphQLService = graphQLProvider;
    }

    @Path("/graphql")
    @POST
    @Produces({MediaType.APPLICATION_JSON})
    @ApiOperation(value = "GraphQL service")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = STATUS_MESSAGE_OK),
            @ApiResponse(code = 405, message = STATUS_MESSAGE_METHOD_NOT_ALLOWED),
            @ApiResponse(code = 204, message = STATUS_MESSAGE_NO_CONTENT),
            @ApiResponse(code = 404, message = STATUS_MESSAGE_NOT_FOUND),
            @ApiResponse(code = 500, message = STATUS_MESSAGE_INTERNAL_ERROR)
    })
    public Response graphql(@HeaderParam(HttpHeaders.CONTENT_TYPE) String contentType, GraphQLRequestBody request) {
        if (request.getQuery() == null) {
            request.setQuery("");
        }
        Object result = graphQLService.execute(request.getQuery());
        return (result != null)? Response.ok(result).build(): Response.status(Response.Status.NO_CONTENT).build();
    }
}
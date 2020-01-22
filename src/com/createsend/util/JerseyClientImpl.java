/**
 * Copyright (c) 2011 Toby Brain
 * 
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *  
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *  
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package com.createsend.util;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJsonProvider;
import org.glassfish.jersey.logging.LoggingFeature;
import org.glassfish.jersey.message.GZipEncoder;

import com.createsend.models.ApiErrorResponse;
import com.createsend.models.PagedResult;
import com.createsend.util.exceptions.CreateSendException;
import com.createsend.util.exceptions.CreateSendHttpException;
import com.createsend.util.jersey.AuthorisedResourceFactory;
import com.createsend.util.jersey.JsonProvider;
import com.createsend.util.jersey.ResourceFactory;
import com.createsend.util.jersey.UnauthorisedResourceFactory;
import com.createsend.util.jersey.UserAgentFilter;
import com.fasterxml.jackson.databind.DeserializationFeature;

public class JerseyClientImpl implements JerseyClient {

	/**
	 * As per Jersey docs the creation of a Client is expensive. We cache the client
	 */
	private static Client client;
	static {

		ClientConfig cc = new ClientConfig(JsonProvider.class);
//		Map<String, Object> properties = cc.getProperties();
//		properties.put(ClientProperties.CHUNKED_ENCODING_SIZE, 64 * 1024);
		cc.property(ClientProperties.CHUNKED_ENCODING_SIZE, 64 * 1024);
//        properties.put(com.sun.jersey.api.json.JSONConfiguration.FEATURE_POJO_MAPPING, "true");
//        client.setFollowRedirects(false);

		client = ClientBuilder
				.newBuilder()
				.withConfig(cc)
				.property(LoggingFeature.LOGGING_FEATURE_VERBOSITY_CLIENT, LoggingFeature.Verbosity.PAYLOAD_ANY)
				.property(LoggingFeature.LOGGING_FEATURE_LOGGER_LEVEL_CLIENT, "WARNING")
				.build();

		if (Configuration.Current.isLoggingEnabled()) {
			client.property(LoggingFeature.LOGGING_FEATURE_VERBOSITY_CLIENT, LoggingFeature.Verbosity.PAYLOAD_ANY);
			client.property(LoggingFeature.LOGGING_FEATURE_LOGGER_LEVEL_CLIENT, "WARNING");
		}

		JacksonJsonProvider jacksonJsonProvider = new JacksonJaxbJsonProvider().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		client.register(jacksonJsonProvider);
		client.register(GZipEncoder.class);
		client.register(new UserAgentFilter());

	}

	private ErrorDeserialiser<String> defaultDeserialiser = new ErrorDeserialiser<String>() {
	};
	private ResourceFactory authorisedResourceFactory;
	private AuthenticationDetails authDetails;

	/**
	 * Constructs a JerseyClientImpl instance, including an OAuth access token and
	 * refresh token.
	 * 
	 * @param auth
	 */
	public JerseyClientImpl(AuthenticationDetails auth) {
		this.setAuthenticationDetails(auth);
	}

	public AuthenticationDetails getAuthenticationDetails() {
		return this.authDetails;
	}

	public void setAuthenticationDetails(AuthenticationDetails authDetails) {
		this.authDetails = authDetails;
		if (authDetails instanceof OAuthAuthenticationDetails) {
			OAuthAuthenticationDetails oauthDetails = (OAuthAuthenticationDetails) authDetails;
			authorisedResourceFactory = new AuthorisedResourceFactory(oauthDetails.getAccessToken());
		} else if (authDetails instanceof ApiKeyAuthenticationDetails) {
			ApiKeyAuthenticationDetails apiKeyDetails = (ApiKeyAuthenticationDetails) authDetails;
			authorisedResourceFactory = new AuthorisedResourceFactory(apiKeyDetails.getApiKey(), "x");
		} else {
			authorisedResourceFactory = new UnauthorisedResourceFactory();
		}
	}

	/**
	 * Performs a HTTP GET on the route specified by the pathElements deserialising
	 * the result to an instance of klass.
	 * 
	 * @param              <T> The type of model expected from the API call.
	 * @param klass        The class of the model to deserialise.
	 * @param pathElements The path of the API resource to access
	 * @return The model returned from the API call
	 * @throws CreateSendException If the API call results in a HTTP status code >=
	 *                             400
	 */
	public <T> T get(Class<T> klass, String... pathElements) throws CreateSendException {
		return get(klass, null, authorisedResourceFactory, pathElements);
	}

	public <T> T get(Class<T> klass, ErrorDeserialiser<?> errorDeserialiser,
			String... pathElements) throws CreateSendException {
		return get(klass, null, authorisedResourceFactory, errorDeserialiser, pathElements);
	}

	/**
	 * Performs a HTTP GET on the route specified by the pathElements deserialising
	 * the result to an instance of klass.
	 * 
	 * @param              <T> The type of model expected from the API call.
	 * @param klass        The class of the model to deserialise.
	 * @param queryString  The query string params to use for the request. Use
	 *                     <code>null</code> when no query string is required.
	 * @param pathElements The path of the API resource to access
	 * @return The model returned from the API call
	 * @throws CreateSendException If the API call results in a HTTP status code >=
	 *                             400
	 */
	public <T> T get(Class<T> klass, MultivaluedMap<String, String> queryString,
			String... pathElements) throws CreateSendException {
		return get(klass, queryString, authorisedResourceFactory, pathElements);
	}

	public <T> T get(Class<T> klass, MultivaluedMap<String, String> queryString,
			ResourceFactory resourceFactory, String... pathElements) throws CreateSendException {
		return get(klass, queryString, resourceFactory, defaultDeserialiser, pathElements);
	}

	public <T> T get(Class<T> klass, MultivaluedMap<String, String> queryString,
			ResourceFactory resourceFactory, ErrorDeserialiser<?> errorDeserialiser,
			String... pathElements) throws CreateSendException {
		WebTarget resource = resourceFactory.getResource(client, pathElements);

		if (queryString != null) {
			resource = queryParams(resource, queryString);
		}
		
		return fixStringResult(klass, request(resource, klass));
	}

	/**
	 * Performs a HTTP GET on the route specified attempting to deserialise the
	 * result to a paged result of the given type.
	 * 
	 * @param              <T> The type of paged result data expected from the API
	 *                     call.
	 * @param queryString  The query string values to use for the request.
	 * @param pathElements The path of the API resource to access
	 * @return The model returned from the API call
	 * @throws CreateSendException If the API call results in a HTTP status code >=
	 *                             400
	 */
	public <T> PagedResult<T> getPagedResult(Integer page, Integer pageSize, String orderField,
			String orderDirection, MultivaluedMap<String, String> queryString, String... pathElements)
			throws CreateSendException {
		WebTarget resource = authorisedResourceFactory.getResource(client, pathElements);
		if (queryString == null)
			queryString = new MultivaluedHashMap();

		addPagingParams(queryString, page, pageSize, orderField, orderDirection);

		try {
			if (queryString != null) {
				resource = queryParams(resource, queryString);
			}

			GenericType<PagedResult<T>> genericType = new GenericType<PagedResult<T>>(getGenericReturnType());
			return request(resource, genericType);
		} catch (SecurityException e) {
			e.printStackTrace();
		}

		return null;
	}

	/**
	 * Posts the provided entity to the url specified by the provided path elements.
	 * The result of the call will be deserialised to an instance of the specified
	 * class.
	 * 
	 * @param              <T> The class to use for model deserialisation
	 * @param klass        The class to use for model deserialisation
	 * @param entity       The entity to use as the body of the post request
	 * @param pathElements The path to send the post request to
	 * @return An instance of klass returned by the api call
	 * @throws CreateSendException Thrown when the API responds with a HTTP Status
	 *                             >= 400
	 */
	public <T> T post(Class<T> klass, Object entity, String... pathElements) throws CreateSendException {
		return post(null, klass, entity, defaultDeserialiser, MediaType.APPLICATION_JSON_TYPE, pathElements);
	}

	public <T> T post(Class<T> klass, MultivaluedMap<String, String> queryString, Object entity, String... pathElements) throws CreateSendException {
		return post(null, klass, queryString, entity, defaultDeserialiser, MediaType.APPLICATION_JSON_TYPE, pathElements);
	}

	public <T> T post(Class<T> klass, Object entity,
			ErrorDeserialiser<?> errorDeserialiser, String... pathElements) throws CreateSendException {
		return post(null, klass, entity, errorDeserialiser, MediaType.APPLICATION_JSON_TYPE, pathElements);
	}

	public <T> T post(String baseUri, Class<T> klass, Object entity, String... pathElements) throws CreateSendException {
		return post(baseUri, klass, entity, defaultDeserialiser, MediaType.APPLICATION_JSON_TYPE, pathElements);
	}

	public <T> T post(String baseUri, Class<T> klass, Object entity,
			ErrorDeserialiser<?> errorDeserialiser, String... pathElements) throws CreateSendException {
		return post(baseUri, klass, entity, errorDeserialiser, MediaType.APPLICATION_JSON_TYPE, pathElements);
	}

	public <T> T post(Class<T> klass, Object entity,
			MediaType mediaType, String... pathElements) throws CreateSendException {
		return post(null, klass, entity, defaultDeserialiser, mediaType, pathElements);
	}

	public <T> T post(String baseUri, Class<T> klass, Object entity,
			MediaType mediaType, String... pathElements) throws CreateSendException {
		return post(baseUri, klass, entity, defaultDeserialiser, mediaType, pathElements);
	}

	public <T> T post(String baseUri, Class<T> klass, Object entity,
			ErrorDeserialiser<?> errorDeserialiser,
			MediaType mediaType, String... pathElements) throws CreateSendException {
		return post(baseUri, klass, null, entity, errorDeserialiser, mediaType, pathElements);
	}

	private <T> T post(String baseUri,
			Class<T> klass,
			MultivaluedMap<String, String> queryString,
			Object entity,
			ErrorDeserialiser<?> errorDeserialiser,
			MediaType mediaType,
			String... pathElements) throws CreateSendException {
		WebTarget resource;
		if (baseUri != null)
			resource = authorisedResourceFactory.getResource(baseUri, client, pathElements);
		else
			resource = authorisedResourceFactory.getResource(client, pathElements);

		if (queryString != null)
			resource = queryParams(resource, queryString);


		Invocation.Builder builder =  resource.request(mediaType);

		return fixStringResult(klass,
				entity == null ? builder.post(Entity.entity("", mediaType), klass) : builder.post(Entity.entity(entity, mediaType), klass));
	}

	/**
	 * Makes a HTTP PUT request to the path specified, using the provided entity as
	 * the request body.
	 * 
	 * @param entity       The entity to use as the request body
	 * @param pathElements The path to make the request to.
	 * @throws CreateSendException Raised when the API responds with a HTTP Status
	 *                             >= 400
	 */
	public void put(Object entity, String... pathElements) throws CreateSendException {
		put(entity, null, defaultDeserialiser, pathElements);
	}

	public <T> T put(Class<T> klass, Object entity, String... pathElements) throws CreateSendException {
		WebTarget resource = authorisedResourceFactory.getResource(client, pathElements);
		Invocation.Builder builder =  resource.request();
		return fixStringResult(klass, builder.put(Entity.entity(entity, MediaType.APPLICATION_JSON_TYPE), klass));
	}

	public void put(Object entity, MultivaluedMap<String, String> queryString, String... pathElements) throws CreateSendException {
		put(entity, queryString, defaultDeserialiser, pathElements);
	}

	public void put(Object entity, ErrorDeserialiser<?> errorDeserialiser,
			String... pathElements) throws CreateSendException {
		put(entity, null, errorDeserialiser, pathElements);
	}

	private void put(Object entity, MultivaluedMap<String, String> queryString, ErrorDeserialiser<?> errorDeserialiser,
			String... pathElements) throws CreateSendException {
		WebTarget resource = authorisedResourceFactory.getResource(client, pathElements);

		if (queryString != null) {
			resource = queryParams(resource, queryString);
		}

		Invocation.Builder builder =  resource.request();
		builder.put(Entity.entity(entity, MediaType.APPLICATION_JSON_TYPE));
	}

	/**
	 * Makes a HTTP DELETE request to the specified path
	 * 
	 * @param pathElements The path of the resource to delete
	 * @throws CreateSendException Raised when the API responds with a HTTP Status
	 *                             >= 400
	 */
	public void delete(String... pathElements) throws CreateSendException {
		delete(null, pathElements);
	}

	/**
	 * Makes a HTTP DELETE request to the specified path with the specified query
	 * string
	 * 
	 * @param pathElements The path of the resource to delete
	 * @throws CreateSendException Raised when the API responds with a HTTP Status
	 *                             >= 400
	 */
	@Override
	public void delete(MultivaluedMap<String, String> queryString, String... pathElements) throws CreateSendException {
		WebTarget resource = authorisedResourceFactory.getResource(client, pathElements);

		if (queryString != null)
			resource = queryParams(resource, queryString);

		Invocation.Builder builder =  resource.request();
		builder.delete();
	}

	protected void addPagingParams(MultivaluedMap<String, String> queryString,
			Integer page, Integer pageSize, String orderField, String orderDirection) {
		if (page != null) {
			queryString.add("page", page.toString());
		}

		if (pageSize != null) {
			queryString.add("pagesize", pageSize.toString());
		}

		if (orderField != null) {
			queryString.add("orderfield", orderField);
		}

		if (orderDirection != null) {
			queryString.add("orderdirection", orderDirection);
		}
	}

	/**
	 * Jersey is awesome in that even though we specify a JSON response and to use
	 * the {@link com.createsend.util.jersey.JsonProvider} it sees that we want a
	 * String result and that the response is already a String so just use that.
	 * This method strips any enclosing quotes required as per the JSON spec.
	 * 
	 * @param        <T> The type of result we are expecting
	 * @param klass  The class of the provided result
	 * @param result The result as deserialised by Jersey
	 * @return If the result if anything but a String just return the result. If the
	 *         result is a String then strip any enclosing quotes (").
	 */
	@SuppressWarnings("unchecked")
	protected <T> T fixStringResult(Class<T> klass, T result) {
		if (klass == String.class) {
			String strResult = (String) result;
			if (strResult.startsWith("\"")) {
				strResult = strResult.substring(1);
			}

			if (strResult.endsWith("\"")) {
				strResult = strResult.substring(0, strResult.length() - 1);
			}

			return (T) strResult;
		}

		return result;
	}

	private ParameterizedType getGenericReturnType() {
		return getGenericReturnType(null, 4);
	}

	public static ParameterizedType getGenericReturnType(Class<?> klass, int stackFrame) {
		StackTraceElement element = Thread.currentThread().getStackTrace()[stackFrame];
		String callingMethodName = element.getMethodName();

		if (klass == null) {
			try {
				klass = Class.forName(element.getClassName());
			} catch (ClassNotFoundException e) {
			}
		}

		if (klass != null) {
			for (Method method : klass.getMethods()) {
				if (method.getName().equals(callingMethodName)) {
					return (ParameterizedType) method.getGenericReturnType();
				}
			}
		}

		return null;
	}

	private WebTarget queryParams(WebTarget resource, MultivaluedMap<String, String> queryString) {
		Set<Entry<String, List<String>>> entrySet = queryString.entrySet();
		for (Entry<String, List<String>> entry : queryString.entrySet()) {
			resource.queryParam(entry.getKey(), entry.getValue());
		}
		return resource;
	}

	private static <T> T request(WebTarget resource, GenericType<T> type) {
		Invocation.Builder invocationBuilder = resource.request();
		Response response = invocationBuilder.get();

		T readEntity = response.readEntity(type);
		return readEntity;
	}
	
	private static <T> T request(WebTarget resource, Class<T> clazz) {
		Invocation.Builder invocationBuilder = resource.request();
		Response response = invocationBuilder.get();

		T readEntity = response.readEntity(clazz);
		return readEntity;
	}
	
}

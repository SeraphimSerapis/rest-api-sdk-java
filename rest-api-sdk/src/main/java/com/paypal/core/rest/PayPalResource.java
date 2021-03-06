package com.paypal.core.rest;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Properties;

import com.paypal.core.ConfigManager;
import com.paypal.core.ConnectionManager;
import com.paypal.core.HttpConfiguration;
import com.paypal.core.HttpConnection;
import com.paypal.core.LoggingManager;

/**
 * PayPalResource acts as a base class for REST enabled resources
 */
public abstract class PayPalResource {

	/**
	 * SDK ID used in User-Agent HTTP header
	 */
	public static final String SDK_ID = "rest-sdk-java";

	/**
	 * SDK Version used in User-Agent HTTP header
	 */
	public static final String SDK_VERSION = "0.5.2";

	/**
	 * Configuration enabled flag
	 */
	private static boolean configInitialized = false;

	/**
	 * Last request sent to Service
	 */
	private static final ThreadLocal<String> LASTREQUEST = new ThreadLocal<String>();

	/**
	 * Last response returned form Service
	 */
	private static final ThreadLocal<String> LASTRESPONSE = new ThreadLocal<String>();

	/**
	 * Initialize using InputStream(of a Properties file)
	 * 
	 * @param is
	 *            InputStream
	 * @throws PayPalRESTException
	 */
	public static void initConfig(InputStream is) throws PayPalRESTException {
		try {
			ConfigManager.getInstance().load(is);
			configInitialized = true;
		} catch (IOException ioe) {
			LoggingManager.severe(PayPalResource.class, ioe.getMessage(), ioe);
			throw new PayPalRESTException(ioe.getMessage(), ioe);
		}

	}

	/**
	 * Initialize using a File(Properties file)
	 * 
	 * @param file
	 *            File object of a properties entity
	 * @throws PayPalRESTException
	 */
	public static void initConfig(File file) throws PayPalRESTException {
		try {
			if (!file.exists()) {
				throw new FileNotFoundException("File doesn't exist: "
						+ file.getAbsolutePath());
			}
			FileInputStream fis = new FileInputStream(file);
			initConfig(fis);
			configInitialized = true;
		} catch (IOException ioe) {
			LoggingManager.severe(PayPalResource.class, ioe.getMessage(), ioe);
			throw new PayPalRESTException(ioe.getMessage(), ioe);
		}

	}

	/**
	 * Initialize using Properties
	 * 
	 * @param properties
	 *            Properties object
	 */
	public static void initConfig(Properties properties) {
		ConfigManager.getInstance().load(properties);
		configInitialized = true;
	}

	/**
	 * Initialize to default properties
	 * 
	 * @throws PayPalRESTException
	 */
	private static void initializeToDefault() throws PayPalRESTException {
		initConfig(PayPalResource.class.getClassLoader().getResourceAsStream(
				"sdk_config.properties"));
	}

	/**
	 * Returns the last request sent to the Service
	 * 
	 * @return Last request sent to the server
	 */
	public static String getLastRequest() {
		return LASTREQUEST.get();
	}

	/**
	 * Returns the last response returned by the Service
	 * 
	 * @return Last response got from the Service
	 */
	public static String getLastResponse() {
		return LASTRESPONSE.get();
	}

	/**
	 * Configures and executes REST call: Supports JSON
	 * 
	 * @param <T>
	 *            Response Type for de-serialization
	 * @param accessToken
	 *            AccessToken to be used for the call.
	 * @param httpMethod
	 *            Http Method verb
	 * @param resource
	 *            Resource URI path
	 * @param payLoad
	 *            Payload to Service
	 * @param clazz
	 *            {@link Class} object used in De-serialization
	 * @return
	 * @throws PayPalRESTException
	 */
	public static <T> T configureAndExecute(String accessToken,
			HttpMethod httpMethod, String resourcePath, String payLoad,
			Class<T> clazz) throws PayPalRESTException {
		T t = null;
		if (!configInitialized) {
			initializeToDefault();
		}
		RESTConfiguration restConfiguration = createRESTConfiguration(
				httpMethod, resourcePath, accessToken, null);

		t = execute(restConfiguration, payLoad, resourcePath, clazz);
		return t;
	}

	/**
	 * Configures and executes REST call: Supports JSON
	 * 
	 * @param <T>
	 *            Response Type for de-serialization
	 * @param apiContext
	 *            {@link APIContext} to be used for the call.
	 * @param httpMethod
	 *            Http Method verb
	 * @param resource
	 *            Resource URI path
	 * @param payLoad
	 *            Payload to Service
	 * @param clazz
	 *            {@link Class} object used in De-serialization
	 * @return
	 * @throws PayPalRESTException
	 */
	public static <T> T configureAndExecute(APIContext apiContext,
			HttpMethod httpMethod, String resourcePath, String payLoad,
			Class<T> clazz) throws PayPalRESTException {
		T t = null;
		if (!configInitialized) {
			initializeToDefault();
		}
		RESTConfiguration restConfiguration = createRESTConfiguration(
				httpMethod, resourcePath, apiContext.getAccessToken(),
				apiContext.getRequestId());

		t = execute(restConfiguration, payLoad, resourcePath, clazz);
		return t;
	}

	/**
	 * Creates a {@link RESTConfiguration} based on configuration
	 * 
	 * @param httpMethod
	 *            {@link HttpMethod}
	 * @param resourcePath
	 *            Resource URI
	 * @param accessToken
	 *            Access Token
	 * @param requestId
	 *            Request Id
	 * @return
	 */
	private static RESTConfiguration createRESTConfiguration(
			HttpMethod httpMethod, String resourcePath, String accessToken,
			String requestId) {
		RESTConfiguration restConfiguration = new RESTConfiguration();
		restConfiguration.setHttpMethod(httpMethod);
		restConfiguration.setResourcePath(resourcePath);
		restConfiguration.setRequestId(requestId);
		restConfiguration.setAuthorizationToken(accessToken);
		return restConfiguration;
	}

	/**
	 * Execute the API call and return response
	 * 
	 * @param <T>
	 *            Type of the return object
	 * @param restConfiguration
	 *            {@link RESTConfiguration}
	 * @param payLoad
	 *            Payload
	 * @param resourcePath
	 *            Resource URI
	 * @param clazz
	 *            Class of the return object
	 * @return API response type object
	 * @throws PayPalRESTException
	 */
	private static <T> T execute(RESTConfiguration restConfiguration,
			String payLoad, String resourcePath, Class<T> clazz)
			throws PayPalRESTException {
		T t = null;
		ConnectionManager connectionManager;
		HttpConnection httpConnection;
		HttpConfiguration httpConfig;
		Map<String, String> headers;
		String responseString;
		try {

			// REST Headers
			headers = restConfiguration.getHeaders();

			// HTTPConfiguration Object
			httpConfig = restConfiguration.getHttpConfigurations();

			// HttpConnection Initialization
			connectionManager = ConnectionManager.getInstance();
			httpConnection = connectionManager.getConnection(httpConfig);
			httpConnection.createAndconfigureHttpConnection(httpConfig);

			LASTREQUEST.set(payLoad);
			responseString = httpConnection.execute(restConfiguration
					.getBaseURL().toURI().resolve(resourcePath).toString(),
					payLoad, headers);
			LASTRESPONSE.set(responseString);
			t = JSONFormatter.fromJSON(responseString, clazz);
		} catch (Exception e) {
			throw new PayPalRESTException(e.getMessage(), e);
		}
		return t;
	}

}

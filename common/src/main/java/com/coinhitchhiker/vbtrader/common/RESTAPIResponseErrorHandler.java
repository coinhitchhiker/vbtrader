package com.coinhitchhiker.vbtrader.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;

import java.io.IOException;

public class RESTAPIResponseErrorHandler extends DefaultResponseErrorHandler {

    private Logger LOGGER = LoggerFactory.getLogger(RESTAPIResponseErrorHandler.class);

    @Override
    public void handleError(ClientHttpResponse response) throws IOException {
        HttpStatus statusCode = getHttpStatusCode(response);
        byte[] responseBody = getResponseBody(response);
        String responseText = new String(responseBody);
        LOGGER.error("------------------HTTP ERROR----------------");
        LOGGER.error(responseText);

        switch (statusCode.series()) {
            case CLIENT_ERROR:
                throw new HttpClientErrorException(statusCode, response.getStatusText(),
                    response.getHeaders(), responseBody, getCharset(response));
            case SERVER_ERROR:
                throw new HttpServerErrorException(statusCode, response.getStatusText(),
                    response.getHeaders(), responseBody, getCharset(response));
            default:
                throw new RestClientException("Unknown status code [" + statusCode + "]");
        }
    }
}

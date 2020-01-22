/**
 * Copyright (c) 2013 James Dennes
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
package com.createsend.util.jersey;

import java.io.IOException;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.HttpHeaders;

/**
 * Client filter adding Authorization header containing OAuth2 bearer token
 * to the HTTP request, if no such header is already present
 */
public final class OAuth2BearerTokenFilter implements ClientRequestFilter {

    private final String authentication;

    /**
     * Creates a new OAuth2 bearer token filter using provided access token.
     *
     * @param accessToken The OAuth2 access token to be used in the HTTP request.
     */
    public OAuth2BearerTokenFilter(final String accessToken) {
        authentication = "Bearer " + accessToken;
    }

	@Override
	public void filter(ClientRequestContext requestContext) throws IOException {
		if (!requestContext.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
			requestContext.getHeaders().add(HttpHeaders.AUTHORIZATION, authentication);
        }
		
	}
}
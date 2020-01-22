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
package com.createsend.util.jersey;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.WebTarget;

import org.glassfish.jersey.client.ClientConfig;

import com.createsend.util.Configuration;

public abstract class ResourceFactory {

    public WebTarget getResource(Client client, String... pathElements) {
    	return getResource(Configuration.Current.getApiEndpoint(),
    			client, pathElements);
    }

	public WebTarget getResource(String baseUri, Client client, String... pathElements) {
		WebTarget resource = client.target(baseUri);
//        resource.setProperty("", 64 * 1024);
//        resource.setProperty(ClientConfig.PROPERTY_CHUNKED_ENCODING_SIZE, 64 * 1024);
//        resource.setProperty(com.sun.jersey.api.json.JSONConfiguration.FEATURE_POJO_MAPPING, "true");

        for (String pathElement : pathElements) {
            resource = resource.path(pathElement);
        }        

        return resource;
	}
}

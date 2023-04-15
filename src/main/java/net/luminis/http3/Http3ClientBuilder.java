/*
 * Copyright © 2019, 2020, 2021, 2022, 2023 Peter Doornbosch
 *
 * This file is part of Flupke, a HTTP3 client Java library
 *
 * Flupke is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Flupke is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package net.luminis.http3;

import net.luminis.quic.log.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executor;

public class Http3ClientBuilder implements HttpClient.Builder {

    private Duration connectTimeout;
    private Long receiveBufferSize;
    private boolean disableCertificateCheck;
    private Logger logger;

    public Http3ClientBuilder receiveBufferSize(long bufferSize) {
        receiveBufferSize = bufferSize;
        return this;
    }

    @Override
    public Http3ClientBuilder cookieHandler(CookieHandler cookieHandler) {
        return this;
    }

    @Override
    public Http3ClientBuilder connectTimeout(Duration duration) {
        connectTimeout = duration;
        return this;
    }

    @Override
    public Http3ClientBuilder sslContext(SSLContext sslContext) {
        return this;
    }

    @Override
    public Http3ClientBuilder sslParameters(SSLParameters sslParameters) {
        return this;
    }

    @Override
    public Http3ClientBuilder executor(Executor executor) {
        return this;
    }

    @Override
    public Http3ClientBuilder followRedirects(HttpClient.Redirect policy) {
        if (policy != HttpClient.Redirect.NEVER) {
            throw new IllegalArgumentException("Follow redirects not supported");
        }
        return this;
    }

    @Override
    public Http3ClientBuilder version(HttpClient.Version version) {
        return this;
    }

    @Override
    public Http3ClientBuilder priority(int priority) {
        return this;
    }

    @Override
    public Http3ClientBuilder proxy(ProxySelector proxySelector) {
        return this;
    }

    @Override
    public Http3ClientBuilder authenticator(Authenticator authenticator) {
        return this;
    }

    public Http3ClientBuilder disableCertificateCheck() {
        disableCertificateCheck = true;
        return this;
    }

    public Http3ClientBuilder logger(Logger logger) {
        this.logger = logger;
        return this;
    }

    @Override
    public HttpClient build() {
        return new Http3Client(connectTimeout, receiveBufferSize, disableCertificateCheck, logger);
    }
}

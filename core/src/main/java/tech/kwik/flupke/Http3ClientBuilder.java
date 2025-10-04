/*
 * Copyright © 2019, 2020, 2021, 2022, 2023, 2024, 2025 Peter Doornbosch
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
package tech.kwik.flupke;

import tech.kwik.core.log.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.InetAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executor;

/**
 * A builder of  {@linkplain Http3Client HTTP/3 Clients}.
 * <p>
 * This builder is intended to work mostly the same as the standard {@link HttpClient.Builder}, but not all methods are
 * (yet) implemented.
 * <p>
 * Notable differences:
 * <ul>the default connection timeout is 35 seconds; for the standard {@link HttpClient} the default connection
 * timeout is platform dependent, but typically between 40 and 150 seconds.
 * </ul>
 */
public class Http3ClientBuilder implements HttpClient.Builder {

    private Duration connectTimeout;
    private Long receiveBufferSize;
    private boolean disableCertificateCheck;
    private Logger logger;
    private int additionalUnidirectionalStreams;
    private int additionalBidirectionalStreams;
    private InetAddress address;

    public Http3ClientBuilder receiveBufferSize(long bufferSize) {
        receiveBufferSize = bufferSize;
        return this;
    }

    /**
     * Binds the socket to this local address when creating connections for sending requests.
     * If no local address is set or null is passed to this method then sockets created by the HTTP client will be bound
     * to an automatically assigned socket address.
     *
     * Common usages of the HttpClient do not require this method to be called. Setting a local address, through this method,
     * is only for advanced usages where users of the HttpClient require specific control on which network interface gets
     * used for the HTTP communication. Callers of this method are expected to be aware of the networking configurations
     * of the system where the HttpClient will be used and care should be taken to ensure the correct localAddr is passed.
     * Failure to do so can result in requests sent through the HttpClient to fail.
     *
     * This method is part of the HttpClient.Builder interface since Java 19.
     *
     * @param localAddr the local address to bind to
     * @return this builder
     */
    public HttpClient.Builder localAddress(InetAddress localAddr) {
        this.address = localAddr;
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

    public Http3ClientBuilder maxAdditionalOpenPeerInitiatedUnidirectionalStreams(int max) {
        if (max < 0) {
            throw new IllegalArgumentException("max must be >= 0");
        }
        additionalUnidirectionalStreams = max;
        return this;
    }

    public Http3ClientBuilder maxAdditionalOpenPeerInitiatedBidirectionalStreams(int max) {
        if (max < 0) {
            throw new IllegalArgumentException("max must be >= 0");
        }
        additionalBidirectionalStreams = max;
        return this;
    }

    public Http3ClientBuilder logger(Logger logger) {
        this.logger = logger;
        return this;
    }

    @Override
    public HttpClient build() {
        return new Http3Client(connectTimeout, receiveBufferSize, disableCertificateCheck, additionalUnidirectionalStreams, additionalBidirectionalStreams, address, logger);
    }
}

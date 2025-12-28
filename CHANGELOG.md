# Releases

## 0.9.4 (2025-12-28)

**Note:** this release introduces the use of request handlers for WebTransport. This might lead to different
behaviour for cases where request handlers were used in combination with WebTransport.
See the `WebTransportEchoServer` sample for explanation.

- added convenience methods to `HttpServerResponse` for adding headers
- added httpbin module providing a limited implementation of a httpbin-server (see `HttpBinServer`)
- added server method to set max header and max data size
- added connect-handle to enable interception for CONNECT method, which can be used to implement for example authorization checks for WebTransport ([issue 25](https://github.com/ptrd/flupke/issues/25))
- reduce memory usage when serializing client request body
- run callback methods on separate threads
- fix: handling of requests that are too large
- fix: handle zero-length data frames correctly
- fix: data frame serialization (when created from partial array)
- fix: write partial byte array to http stream (used in WebTransport only)
- improved client error handling

## 0.9.3 (2025-11-27)

- added method to let server http request object provide the authority (equivalent to HTTP/1 "Host" header)

## 0.9.2 (2025-11-25)

- added method to let server http request object provide the client address (IP and port number)

## 0.9.1 (2025-11-11)

- fix for WebTransport server: incorrect frameType was read when stream did not yet provide enough data
- fix for HTTP/3 client: flow subscription should asynchronously publish its items
- support custom executors in Http3ApplicationProtocolFactory

## 0.9 (2025-10-06)

- define Java modules ([issue 14](https://github.com/ptrd/flupke/issues/14))
- moved `WebTransportHttp3ApplicationProtocolFactory` to websocket base package (`tech.kwik.flupke.webtransport`)
- introduced builder for websocket `ClientSessionFactory`
- moved some internal classes to other packages
- made `HttpServerRequest` and `HttpServerResponse` an interface
- moved `FlupkeVersion`, `HttpError` and `HttpStream` to flupke base package (`tech.kwik.flupke`)
- transformed gradle build to a multi-project build
- make client support large data frames when running with limited memory
- added methods to set key manager and custom trustmanager ([issue 5](https://github.com/ptrd/flupke/issues/5))

## 0.8 (2025-08-29)

- server handler: receive request body (as stream)

## 0.7 (2025-08-22)

- added support for WebTransport ([draft-13](https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-13.html)) ([issue 1](https://github.com/ptrd/flupke/issues/1))
- server handler: receive request headers and set response headers
- upgraded kwik dependency to latest (0.10.4)
- upgraded qpack dependency to latest (2.0.1)

## 0.6 (2025-01-21)

**Note: this release has a breaking change (which is, however, easy to fix)**

Changed package structure: the flupke package (and all subpackages) now start with `tech.kwik.flupke`.

**Upgrade instructions:** perform a global find-and-replace to replace the string `net.luminis.http3` by `tech.kwik.flupke`.
If your project is also using the android drop-in replacement for the Java HTTP Client, also replace the string `net.luminis.httpclient` by `tech.kwik.flupke.httpclient`.

## 0.5.4 (2025-01-11)

- upgraded kwik dependency to latest (0.10)
- upgraded qpack dependency to latest (2.0)
- added method `localAddress(InetAddress localAddr)` to `HttpClient.Builder` (thanks to [kdhDevelop](https://github.com/kdhDevelop) for providing pull request)
- increased default connect timeout from 5 seconds to 35 (thanks to [Alexander Schütz](https://github.com/AlexanderSchuetz97) for reporting this issue)
- moved `FileServer` class to sample package
- deprecated `Http3ApplicationProtocolFactory(File wwwDir)` constructor
- added sample `Http3FileServer`
- fix: do not create a new (server) thread for each request
- added `Http3ApplicationProtocolFactory(HttpRequestHandler requestHandler)` constructor 

## 0.5.3 (2023-12-30)

Upgraded kwik dependency to latest (0.8.8) and made some related changes to limit resource usage.

## 0.5.2 (2023-11-17)

Relocated maven artifact to `tech.kwik` group id.

## 0.5.1 (2023-11-05)

No changes, corrected pom.

## 0.5 (2023-10-10)

First official release published to maven.

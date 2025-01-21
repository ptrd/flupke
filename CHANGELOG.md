# Releases

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

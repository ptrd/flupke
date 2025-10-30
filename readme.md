![Flupke](https://bitbucket.org/pjtr/flupke/raw/master/docs/Logo%20Flupke%20rectangle.png)

## HTTP3 Java

Flupke is a (100% pure) Java HTTP3 implementation that runs on top of [Kwik](http://kwik.tech).

HTTP3 is a new standard that has been developed by the Internet Engineering Task Force (IETF) and is specified by 
[RFC 9114](https://www.rfc-editor.org/rfc/rfc9114.html).
HTTP3 uses QUIC as transport protocol and QPACK for header compression. 
Flupke builds on [Kwik](https://github.com/ptrd/kwik), a Java implementation of QUIC;
header compression is supported by the [QPACK](https://github.com/ptrd/qpack) library.

Initially, Flupke was only a HTTP3 Client, but since June 2021 it contains everything to build and run an HTTP/3 Server.

WebTransport ([draft-ietf-webtrans-http3-13](https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-13.html)) is also supported,
but is still experimental.
See [samples](https://github.com/ptrd/flupke/tree/master/samples/src/main/java/tech/kwik/flupke/sample/webtransport) for how to create WebTransport client or server.

Flupke is created and maintained by Peter Doornbosch. The latest greatest can always be found on [ptrd@GitHub](https://github.com/ptrd/flupke).

## Usage

Flupke releases are available on [Maven Central](https://central.sonatype.com/artifact/tech.kwik/flupke/versions).
Flupke's Maven group ID is `tech.kwik`, and its artifact ID is `flupke`. 
Check [versions](https://central.sonatype.com/artifact/tech.kwik/flupke/versions) to find 
the version number of the latest release and samples for various build tools of how to add the dependency to your project. 

The samples are also published on Maven Central, the artifact ID is `flupke-samples`
and its version(s) can be found [here](https://central.sonatype.com/artifact/tech.kwik/flupke-samples/versions).

Of course, you can also clone this repository and build Flupke and the samples yourself; see below for more details.

### API

Flupke client uses the HTTP Client API introduced with Java 11, e.g. 

    HttpClient.Builder clientBuilder = new Http3ClientBuilder();
    HttpClient client = clientBuilder.build();
    HttpResponse<String> httpResponse = client.send(request, HttpResponse.BodyHandlers.ofString());

See the [Sample](https://github.com/ptrd/flupke/blob/master/samples/src/main/java/tech/kwik/flupke/sample/Sample.java) 
class for a working example and to learn about various Flupke-specific builder options.

Flupke also supports POST requests, or more generally, HTTP methods that require or use a request body. 
See the [PostExample](https://github.com/ptrd/flupke/blob/master/samples/src/main/java/tech/kwik/flupke/sample/PostExample.java) for details.

Building a server is straight forward: provide an implementation of [HttpRequestHandler](https://github.com/ptrd/flupke/blob/master/core/src/main/java/tech/kwik/flupke/server/HttpRequestHandler.java),
which contains only one method:

    void handleRequest(HttpServerRequest request, HttpServerResponse response) throws IOException;

and off you go! For a working example, see [Http3FileServer](https://github.com/ptrd/flupke/blob/master/samples/src/main/java/tech/kwik/flupke/sample/Http3FileServer.java)

### Java Modules System

Flupke defines two JPMS modules:

- `tech.kwik.flupke`, which contains everything for client and server
- `tech.kwik.flupke.samples`, which contains all samples

### Work in progress

This project (as well as the projects it builds on) is work in progress.

Features:

- HTTP3 request & response with all methods (GET, PUT, POST etc)
- Multiplexing of HTTP3 requests over one underlying QUIC connection
- Support for asynchronous handling with ```HttpClient.sendAsync()```
- Supports both CONNECT method and extended CONNECT [RFC 9220](https://www.rfc-editor.org/rfc/rfc9220.html)
- experimental support for WebTransport [draft-ietf-webtrans-http3-13](https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-13.html)

Limitations in the current version:

- QPack dynamic table is not supported, neither does the QPack _encoder_ use Huffman encoding. Note that even with these
  limitations, Flupke can talk to any HTTP/3 compliant server.
- No support for server push.
- No support for GOAWAY.


Also note that `Http3Client.version()` returns null instead of a proper Version object; 
this is unavoidable as the Java [HTTPClient.Version](https://docs.oracle.com/en/java/javase/11/docs/api/java.net.http/java/net/http/HttpClient.Version.html)
enum does not provide a value for HTTP3. See [JDK-8229533](https://bugs.java.com/bugdatabase/view_bug.do?bug_id=JDK-8229533).

## Build & run

Building is done by gradle; the gradle wrapper is included in the sources, so after checking out the source, just run

    ./gradlew build
    
which will run the unit tests and create the `flupke.jar` file in `build/libs`.
This `flupke.jar` contains the Flupke library (including QPack classes). 
When using in your own project, you will also need the Kwik dependency, which you can fetch from Maven Central.

Alternatively, you can build the uberjar, which contains all dependencies and provides a runnable sample client.
To build the uberjar, run

    ./gradlew -p core uberjar

To run the sample client, use the provided `flupke.sh` shell script and pass the target URL as a parameter.
You can also run the java command directly:

    java -cp core/build/libs/flupke-*uber.jar:samples/build/libs/flupke-samples*.jar tech.kwik.flupke.sample.Sample <URL>

Whether the URL is specified with HTTP or HTTPS protocol doesn't matter, Flupke will always (and only) try to set up a QUIC connection.
The port specified in the URL must be the UDP port on which the HTTP3/QUIC server listens.

Gradle can also generate IntelliJ Idea project files for you:

    gradle idea

By default, Flupke will use QUIC version 1 (the official RFC version). To let Flupke use a different QUIC version (e.g. QUIC version 2, RFC 9369), put the version in an environment variable called "QUIC_VERSION" (for QUIC version 2, use the value "2" (without the quotes)).

The project requires Java 11.

## Contact

If you have questions about this project, please mail the author (peter dot doornbosch) at luminis dot eu.

## Acknowledgements

Thanks to Piet van Dongen for creating the marvellous logo!

## License

This program is open source and licensed under LGPL (see the LICENSE.txt and LICENSE-LESSER.txt files in the distribution). 
This means that you can use this program for anything you like, and that you can embed it as a library in other applications, even commercial ones. 
If you do so, the author would appreciate if you include a reference to the original.
 
As of the LGPL license, all modifications and additions to the source code must be published as (L)GPL as well.

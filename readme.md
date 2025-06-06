![Flupke](https://bitbucket.org/pjtr/flupke/raw/master/docs/Logo%20Flupke%20rectangle.png)

## HTTP3 Java

Flupke is a Java HTTP3 implementation that runs on top of [Kwik](http://kwik.tech).

HTTP3 is a new standard that has been developed by the Internet Engineering Task Force (IETF) and is specified by 
[RFC 9114](https://www.rfc-editor.org/rfc/rfc9114.html).
HTTP3 uses QUIC as transport protocol and QPACK for header compression. 
Flupke builds on [Kwik](http://kwik.tech), a Java implementation of QUIC;
header compression is supported by the [QPACK](https://bitbucket.org/pjtr/qpack/) library.

Initially, Flupke was only a HTTP3 Client, but since June 2021 it also provides a plugin that, when used with Kwik,
acts as a (simple) HTTP3 webserver server.

Flupke is created and maintained by Peter Doornbosch. The latest greatest can always be found on [BitBucket](https://bitbucket.org/pjtr/flupke/).

## Usage

Latest release can be found at maven central:

    <dependency>
        <groupId>tech.kwik</groupId>
        <artifactId>flupke</artifactId>
        <version>0.5.4</version>
    </dependency> 


Flupke uses the HTTP Client API introduced with Java 11, e.g. 

    HttpClient.Builder clientBuilder = new Http3ClientBuilder();
    HttpClient client = clientBuilder.build();
    HttpResponse<String> httpResponse = client.send(request, HttpResponse.BodyHandlers.ofString());

See the [Sample](https://github.com/ptrd/flupke/blob/master/src/main/java/tech/kwik/flupke/sample/Sample.java) 
class for a working example.

Flupke also supports POST requests, or more generally, HTTP methods that require or use a request body. 
See the [PostExample](https://github.com/ptrd/flupke/blob/master/src/main/java/tech/kwik/flupke/sample/PostExample.java) for details.


### Work in progress

This project (as well as the projects it builds on) is work in progress.

Features:

- HTTP3 request & response with all methods (GET, PUT, POST etc)
- Multiplexing of HTTP3 requests over one underlying QUIC connection
- Support for asynchronous handling with ```HttpClient.sendAsync()```

Limitations in the current version:

- QPack dynamic table is not supported, neither does the QPack _encoder_ use Huffman encoding. Note that even with these
  limitations, Flupke can talk to any HTTP/3 compliant server.
- No support for server push.
- No support for CONNECT method.
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

    ./gradlew uberjar

To run the sample client, use the provided `flupke.sh` shell script and pass the targer URL as a parameter.
You can also run the java command directly:

    java -cp build/libs/flupke-uber.jar tech.kwik.flupke.sample.Sample <URL>

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

![Flupke](https://bitbucket.org/pjtr/flupke/raw/master/docs/Logo%20Flupke%20rectangle.png)

## HTTP3 Java Client

Flupke is a Java HTTP3 Client. 
Flupke uses [Kwik](http://kwik.tech), a Java implementation of QUIC, as transport protocol. 
Currently, Flupke supports the [HTTP3 draft-22](https://tools.ietf.org/html/draft-ietf-quic-http-22) version 
and uses [QPACK version draft-8](https://tools.ietf.org/html/draft-ietf-quic-qpack-08) 
and [QUIC version draft-22](https://tools.ietf.org/html/draft-ietf-quic-transport-22).

## Usage

Flupke supports the "new" HTTP Client API, e.g. 

    HttpClient.Builder clientBuilder = new Http3ClientBuilder();
    HttpClient client = clientBuilder.build();
    HttpResponse<String> httpResponse = client.send(request, HttpResponse.BodyHandlers.ofString());

See the [Sample](https://bitbucket.org/pjtr/flupke/src/master/src/main/java/net/luminis/http3/Sample.java)
class for a working example.

### Limitations

As this project just started, the current version has some limitations:

- only synchronous requests are supported (i.e. `client.sendAsync()` won't do anything usefull)
- request headers are ignored
- POST and PUT are not supported (because the request body is ignored)
- each send creates a new HTTP3 (and thus a new QUIC) connection, so multiplexing is not supported.
  Of course, support for multiplexing _will_ be added soon, as this is one of the major features of HTTP3/QUIC. 

Also note that `Http3Client.version()` returns null instead of a proper Version object; 
this is unavoidable as the Java [HTTPClient.Version](https://docs.oracle.com/en/java/javase/11/docs/api/java.net.http/java/net/http/HttpClient.Version.html)
enum does not provide a value for HTTP3.

## Build & run

Flupke uses git submodules for dependencies, so make sure when you clone the git repo, 
you include the submodules or add them later with

    git submodule update --init --recursive

Building is done by gradle, but the gradle wrapper is included in the sources, so after checking out the source, just run

    ./gradlew build
    
which will run the unit tests and create a jar file in `build/libs`.

Gradle can also generate IntelliJ Idea project files for you:

    gradle idea
    
To run the sample, use the provided `flupke.sh` shell script and pass the targer URL as a parameter.
You can also run the java command directly:

    java -cp build/libs/flupke.jar net.luminis.http3.Sample <URL>

Whether the URL is specified with HTTP or HTTPS protocol doesn't matter, Flupke will always (and only) try to setup a QUIC connection.
The port specified in the URL must be the UDP port on which the HTTP3/QUIC server listens. 

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

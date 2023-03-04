
## Flupke on Android

To use Flupke on Android, a custom build is necessary, because Flupke uses the
[Java HTTP Client](https://docs.oracle.com/en/java/javase/11/docs/api/java.net.http/java/net/http/HttpClient.html)
introduced in Java 11. Unfortunately, this API is not (yet?) supported by Android.
This project provides a (partial) drop-in replacement for the Java HTTP Client, see [httpclient](./httpclient).
It's not a complete Java HTTP Client, it contains just enough of the interfaces and a few implementation classes to 
make Flupke compile and run without the Java 11 HTTP Client.

To create the flupke-on-android library, just `cd` to _this_ directory (`<project-dir>/android`) and execute 

```gradle build```

and find the `flupke-android.jar` in the `build/libs/` directory.

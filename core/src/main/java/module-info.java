module tech.kwik.flupke {
    exports tech.kwik.flupke;
    exports tech.kwik.flupke.server;
    exports tech.kwik.flupke.webtransport;

    requires transitive tech.kwik.core;
    requires tech.kwik.qpack;
    requires java.net.http;
}


module tech.kwik.flupke.samples {
    exports tech.kwik.flupke.sample;
    exports tech.kwik.flupke.sample.kwik;
    exports tech.kwik.flupke.sample.webtransport;
    exports tech.kwik.flupke.sample.webtransport.baton;

    requires tech.kwik.flupke;
    requires tech.kwik.core;
    requires java.net.http;
}
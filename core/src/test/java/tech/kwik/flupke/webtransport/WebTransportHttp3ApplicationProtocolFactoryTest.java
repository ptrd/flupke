/*
 * Copyright © 2026 Peter Doornbosch
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
package tech.kwik.flupke.webtransport;

import org.junit.jupiter.api.Test;
import tech.kwik.flupke.server.Http3ApplicationProtocolFactory;
import tech.kwik.flupke.server.Http3ServerExtensionFactory;
import tech.kwik.flupke.server.HttpRequestHandler;
import tech.kwik.flupke.webtransport.impl.WebTransportExtensionFactory;

import java.lang.reflect.Field;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class WebTransportHttp3ApplicationProtocolFactoryTest {

    @Test
    void factoryRegistersBothWebtransportProtocolNames() throws Exception {
        WebTransportHttp3ApplicationProtocolFactory factory =
                new WebTransportHttp3ApplicationProtocolFactory(mock(HttpRequestHandler.class));

        Map<String, Http3ServerExtensionFactory> extensions = getExtensions(factory);

        // draft-ietf-webtrans-http3-15 mandates ":protocol" = "webtransport-h3"
        assertThat(extensions).containsKey("webtransport-h3");
        // draft-ietf-webtrans-http3-13 (backward compatibility) uses ":protocol" = "webtransport"
        assertThat(extensions).containsKey("webtransport");

        assertThat(extensions.get("webtransport-h3")).isInstanceOf(WebTransportExtensionFactory.class);
        // Both protocol names must resolve to the same extension factory instance.
        assertThat(extensions.get("webtransport")).isSameAs(extensions.get("webtransport-h3"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Http3ServerExtensionFactory> getExtensions(WebTransportHttp3ApplicationProtocolFactory factory) throws Exception {
        Field field = Http3ApplicationProtocolFactory.class.getDeclaredField("extensions");
        field.setAccessible(true);
        return (Map<String, Http3ServerExtensionFactory>) field.get(factory);
    }
}

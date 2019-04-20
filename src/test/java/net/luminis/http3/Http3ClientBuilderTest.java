/*
 * Copyright © 2019 Peter Doornbosch
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
package net.luminis.http3;

import org.junit.Test;
import java.net.http.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;


public class Http3ClientBuilderTest {

    @Test
    public void testBuilderCreatesHttp3Client() {
        HttpClient client = new Http3ClientBuilder().build();

        assertThat(client).isInstanceOf(Http3Client.class);
    }
}

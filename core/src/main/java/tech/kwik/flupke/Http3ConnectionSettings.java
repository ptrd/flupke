/*
 * Copyright © 2024, 2025 Peter Doornbosch
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
package tech.kwik.flupke;

import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509TrustManager;

public interface Http3ConnectionSettings {

    /**
     * Disables validation of the server's certificate on the underlying QUIC connection.
     * WARNING: using this makes the connection INSECURE and vulnerable to man-in-the-middle attacks. Only use this for
     * testing purposes.
     * @return
     */
    boolean disableCertificateCheck();

    /**
     * Get the custom trust manager, the source of peer authentication trust decisions.
     * @return   the custom trust manager, or null if the system default is to be used
     */
    X509TrustManager trustManager();

    /**
     * Get the custom key manager, the source of authentication keys and certificates.
     * @return
     */
    X509ExtendedKeyManager keyManager();

    /**
     * The maximum number of additional unidirectional streams that the peer is allowed to initiate.
     * Should only be used by HTTP/3 extensions!
     * Only useful for HTTP/3 extensions that need more peer initiated unidirectional streams than the ones that are
     * used/allowed by HTTP/3 itself.
     * @return
     */
    int maxAdditionalPeerInitiatedUnidirectionalStreams();

    /**
     * The maximum number of additional bidirectional streams that the peer is allowed to initiate.
     * Should only be used by HTTP/3 extensions!
     * Only useful for HTTP/3 extensions that need more peer initiated bidirectional streams than the ones that are
     * used/allowed by HTTP/3 itself.
     * @return
     */
    int maxAdditionalPeerInitiatedBidirectionalStreams();
}

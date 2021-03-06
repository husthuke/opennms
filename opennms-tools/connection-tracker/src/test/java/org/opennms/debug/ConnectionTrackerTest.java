/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2006-2014 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2014 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.debug;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.channels.DatagramChannel;
import java.nio.channels.Selector;
import java.sql.Connection;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.mchange.v2.c3p0.ComboPooledDataSource;

public class ConnectionTrackerTest {
    @Before
    public void setUp() {
    }

    @Test
    @Ignore
    public void testC3poDatabaseConnection() throws Exception {
        final ComboPooledDataSource cpds = new ComboPooledDataSource();
        cpds.setDriverClass("org.postgresql.Driver");
        cpds.setUser("opennms");
        cpds.setJdbcUrl("jdbc:postgresql://localhost:5432/template1");
        
        final Connection conn = cpds.getConnection();
        assertEquals(1, Connections.getPooledConnectionCount());
        Connections.printPooledStatus();
        
        conn.close();
        assertEquals(0, Connections.getPooledConnectionCount());
        Connections.printPooledStatus();
    }

    @Test
    @Ignore
    public void test() throws IOException {

        Selector selector = Selector.open();

        assertTrue(selector.isOpen());

        selector.close();

        assertFalse(selector.isOpen());


        DatagramChannel c = DatagramChannel.open();
        DatagramSocket s = c.socket();
        s.setSoTimeout(1000);

        byte[] buf = new byte[1024];

        DatagramPacket p = new DatagramPacket(buf, 1024, InetAddress.getLocalHost(), 7);

        s.send(p);


    }

}

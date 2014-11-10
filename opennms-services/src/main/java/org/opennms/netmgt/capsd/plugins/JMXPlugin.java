/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2005-2014 The OpenNMS Group, Inc.
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

package org.opennms.netmgt.capsd.plugins;

import org.opennms.core.utils.InetAddressUtils;
import org.opennms.netmgt.capsd.AbstractPlugin;
import org.opennms.netmgt.jmx.JmxUtils;
import org.opennms.netmgt.jmx.connection.JmxServerConnectionWrapper;
import org.opennms.netmgt.jmx.impl.connection.connectors.DefaultConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.Map;


/*
 * This is the base class that handles the actual capability test.  The subclass is responsible 
 * for getting the ConnectionWrapper that is used to attempt to contact the remote resource.
 * 
 * @author <A HREF="mailto:mike@opennms.org">Mike Jamison </A>
 * @author <A HREF="http://www.opennms.org/">OpenNMS </A>
 */
/**
 * <p>Abstract JMXPlugin class.</p>
 *
 * @author ranger
 * @version $Id: $
 */
public abstract class JMXPlugin extends AbstractPlugin {
    
    
    private static final Logger LOG = LoggerFactory.getLogger(JMXPlugin.class);
    
    private String protocolName = null;

    /* The subclass can use set the protocol name from the parameters map
     * 
     * @see org.opennms.netmgt.capsd.Plugin#getProtocolName()
     */
    /**
     * <p>Getter for the field <code>protocolName</code>.</p>
     *
     * @param parameterMap a {@link java.util.Map} object.
     * @return a {@link java.lang.String} object.
     */
    public abstract String getProtocolName(Map<String, Object> parameterMap);
    
    protected abstract String getConnectionName();
    
    /*
     * @see org.opennms.netmgt.capsd.Plugin#getProtocolName()
     */
    /**
     * <p>Getter for the field <code>protocolName</code>.</p>
     *
     * @return a {@link java.lang.String} object.
     */
    @Override
    public String getProtocolName() {
        return protocolName.toUpperCase();
    }

    /* (non-Javadoc)
     * @see org.opennms.netmgt.capsd.Plugin#isProtocolSupported(java.net.InetAddress, java.util.Map)
     */

    /** {@inheritDoc} */
    @Override
    public boolean isProtocolSupported(InetAddress address, Map<String, Object> map) {
        
        if (protocolName == null) {
            protocolName = getProtocolName(map);
        }

        boolean res = false;
        JmxServerConnectionWrapper connection = null;
        try {

            connection = new DefaultConnectionManager(1).connect(getConnectionName(), InetAddressUtils.str(address), JmxUtils.convertToStringMap(map), null);

            Integer result = connection.getMBeanServerConnection().getMBeanCount();
            LOG.debug("isProtocolSupported? {} {} {}", getProtocolName(), result, connection);
            if (result != null) {
                res = true;
            }
        } catch (Throwable e) {
            LOG.debug("{} - isProtocolSupported - failed! {}", getProtocolName(map), InetAddressUtils.str(address));
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
        
        return res;
    }
}


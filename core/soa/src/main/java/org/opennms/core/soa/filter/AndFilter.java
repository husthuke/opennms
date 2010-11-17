/*
 * This file is part of the OpenNMS(R) Application.
 *
 * OpenNMS(R) is Copyright (C) 2010 The OpenNMS Group, Inc.  All rights reserved.
 * OpenNMS(R) is a derivative work, containing both original code, included code and modified
 * code that was published under the GNU General Public License. Copyrights for modified
 * and included code are below.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * For more information contact:
 * OpenNMS Licensing       <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 */
package org.opennms.core.soa.filter;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.opennms.core.soa.Filter;

/**
 * AndFilter
 *
 * @author brozow
 */
public class AndFilter extends AbstractFilter {
    
    List<Filter> m_filters;

    public AndFilter(List<Filter> filters) {
        m_filters = filters;
    }
    
    public AndFilter(Filter... filters) {
        this(Arrays.asList(filters));
    }

    @Override
    public boolean match(Map<String, String> properties) {
        for(Filter f : m_filters) {
            if (!f.match(properties)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("(&");
        for(Filter f : m_filters) {
            buf.append(f);
        }
        buf.append(")");
        return buf.toString();
    }

}

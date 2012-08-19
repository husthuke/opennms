/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2009-2011 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2011 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.netmgt.provision.service.vmware;

import com.vmware.vim25.*;
import com.vmware.vim25.mo.*;
import org.apache.commons.io.IOExceptionWithCause;
import org.exolab.castor.xml.MarshalException;
import org.exolab.castor.xml.ValidationException;
import org.opennms.core.utils.url.GenericURLConnection;
import org.opennms.core.xml.JaxbUtils;
import org.opennms.netmgt.model.PrimaryType;
import org.opennms.netmgt.collectd.vmware.VmwareViJavaAccess;
import org.opennms.netmgt.provision.persist.requisition.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.JAXBException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.rmi.RemoteException;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The Class VmwareRequisitionUrlConnection
 * <p/>
 * This class is used for the automtic requisition of Vmware related entities.
 *
 * @author Christian Pape <Christian.Pape@informatik.hs-fulda.de>
 */
public class VmwareRequisitionUrlConnection extends GenericURLConnection {
    /**
     * the logger
     */
    private Logger logger = LoggerFactory.getLogger(VmwareRequisitionUrlConnection.class);

    private static final int VMWARE_HOSTSYSTEM = 1;
    private static final int VMWARE_VIRTUALMACHINE = 2;

    private final static String VMWARE_HOSTSYSTEM_SERVICES = "hostSystemServices";
    private final static String VMWARE_VIRTUALMACHINE_SERVICES = "virtualMachineServices";

    private String[] m_hostSystemServices; // default = {"VMware-ManagedEntity", "VMware-HostSystem", "VMwareCim-HostSystem"};
    private String[] m_virtualMachineServices; // default = {"VMware-ManagedEntity", "VMware-VirtualMachine"};

    private String m_hostname = null;
    private String m_username = null;
    private String m_password = null;
    private String m_foreignSource = null;

    private boolean m_importVMPoweredOn = true;
    private boolean m_importVMPoweredOff = false;
    private boolean m_importVMSuspended = false;

    private boolean m_importHostPoweredOn = true;
    private boolean m_importHostPoweredOff = false;
    private boolean m_importHostStandBy = false;
    private boolean m_importHostUnknown = false;

    /**
     * the query args
     */
    private static Map<String, String> m_args = null;

    /**
     * requisition object
     */
    private Requisition m_requisition = null;

    /**
     * Constructor for creating an instance of this class.
     *
     * @param url the URL to use
     * @throws MalformedURLException
     * @throws RemoteException
     */
    public VmwareRequisitionUrlConnection(URL url) throws MalformedURLException, RemoteException {
        super(url);

        m_hostname = url.getHost();

        m_username = getUsername();
        m_password = getPassword();

        m_args = getQueryArgs();

        m_importVMPoweredOn = queryParameter("importVMPoweredOn", true);
        m_importVMPoweredOff = queryParameter("importVMPoweredOff", false);
        m_importVMSuspended = queryParameter("importVMSuspended", false);

        m_importHostPoweredOn = queryParameter("importHostPoweredOn", true);
        m_importHostPoweredOff = queryParameter("importHostPoweredOff", false);
        m_importHostStandBy = queryParameter("importHostStandBy", false);
        m_importHostUnknown = queryParameter("importHostUnknown", false);

        // get services to be added to host systems
        m_hostSystemServices = getHostSystemServices();

        // get services to be added to virtual machines
        m_virtualMachineServices = getVirtualMachineServices();

        String path = url.getPath();

        path = path.replaceAll("^/", "");
        path = path.replaceAll("/$", "");

        String pathElements[] = path.split("/");

        if (pathElements.length == 1) {
            if ("".equals(pathElements[0])) {
                m_foreignSource = "vmware-" + m_hostname;
            } else {
                m_foreignSource = pathElements[0];
            }
        } else {
            throw new MalformedURLException("Error processing path element of URL (vmware://username:password@host[/foreign-source]?keyA=valueA;keyB=valueB;...)");
        }
    }

    /**
     * Determine services for host systems to be provisioned from URL
     *
     * @return a String[] of opennms service names
     */
    private String[] getHostSystemServices() {
        String[] hostSystemsServices = new String[]{"VMware-ManagedEntity", "VMware-HostSystem", "VMwareCim-HostSystem"};
        if (m_args != null && m_args.get(VMWARE_HOSTSYSTEM_SERVICES) != null) {
            hostSystemsServices = m_args.get(VMWARE_HOSTSYSTEM_SERVICES).split(",");
        }
        return hostSystemsServices;
    }

    /**
     * Determine services for virtual machines to be provisioned from URL
     *
     * @return a String[] of opennms service names
     */
    private String[] getVirtualMachineServices() {
        String[] virtualMachineServices = new String[]{"VMware-ManagedEntity", "VMware-VirtualMachine"};
        if (m_args != null && m_args.get(VMWARE_VIRTUALMACHINE_SERVICES) != null) {
            virtualMachineServices = m_args.get(VMWARE_VIRTUALMACHINE_SERVICES).split(",");
        }
        return virtualMachineServices;
    }

    /**
     * Returns a boolean representation for a given on/off parameter.
     *
     * @param key          the parameter's name
     * @param defaultValue the default value to use
     * @return the boolean value
     */
    private boolean queryParameter(String key, boolean defaultValue) {
        if (m_args.get(key) == null) {
            return defaultValue;
        } else {
            String value = m_args.get(key).toLowerCase();

            return ("yes".equals(value) || "true".equals(value) || "on".equals(value) || "1".equals(value));
        }
    }

    @Override
    public void connect() throws IOException {
        // To change body of implemented methods use File | Settings | File
        // Templates.
    }

    /**
     * Creates a requisition node for the given managed entity and type.
     *
     * @param ipAddresses       the set of Ip addresses
     * @param managedEntity     the managed entity
     * @param managedEntityType the type of entity
     * @return the generated requisition node
     */
    private RequisitionNode createRequisitionNode(Set<String> ipAddresses, ManagedEntity managedEntity, int managedEntityType) {
        RequisitionNode requisitionNode = new RequisitionNode();

        // Setting the node label
        requisitionNode.setNodeLabel(managedEntity.getName());

        // Foreign Id consisting of managed entity Id
        requisitionNode.setForeignId(managedEntity.getMOR().getVal());

        /*
         * Original version:
         *
         * Foreign Id consisting of VMware management server's hostname and managed entity id
         *
         * requisitionNode.setForeignId(m_hostname + "/" + managedEntity.getMOR().getVal());
         */

        boolean primary = true;

        // add all given interfaces
        for (String ipAddress : ipAddresses) {

            try {
                InetAddress inetAddress = InetAddress.getByName(ipAddress);

                if (!inetAddress.isLoopbackAddress()) {
                    RequisitionInterface requisitionInterface = new RequisitionInterface();
                    requisitionInterface.setIpAddr(ipAddress);

                    //  the first one will be primary
                    if (primary) {
                        requisitionInterface.setSnmpPrimary(PrimaryType.PRIMARY);

                        if (managedEntityType == VMWARE_HOSTSYSTEM) {
                            for (String service : m_hostSystemServices) {
                                requisitionInterface.insertMonitoredService(new RequisitionMonitoredService(service.trim()));
                            }
                        } else {
                            for (String service : m_virtualMachineServices) {
                                requisitionInterface.insertMonitoredService(new RequisitionMonitoredService(service.trim()));
                            }
                        }

                        primary = false;
                    } else {
                        requisitionInterface.setSnmpPrimary(PrimaryType.SECONDARY);
                    }

                    requisitionInterface.setManaged(Boolean.TRUE);
                    requisitionInterface.setStatus(Integer.valueOf(1));
                    requisitionNode.putInterface(requisitionInterface);
                }
            } catch (UnknownHostException unknownHostException) {
                logger.warn("Invalid IP address '{}'", unknownHostException.getMessage());
            }
        }
        /*
         * For now we use displaycategory, notifycategory and pollercategory for storing
         * the vcenter Ip address, the username and the password
         */

        String vmNetworks = "";
        String vmDatastores = "";
        String vmRuntimeInformation = "";
        String vmPowerState = "unknown";

        if (managedEntityType == VMWARE_HOSTSYSTEM) {
            HostSystem hostSystem = (HostSystem) managedEntity;

            vmPowerState = hostSystem.getSummary().getRuntime().getPowerState().toString();

            try {
                for (Datastore datastore : hostSystem.getDatastores()) {
                    if (!"".equals(vmDatastores)) {
                        vmDatastores += ", ";
                    }
                    vmDatastores += datastore.getMOR().getVal();
                }
            } catch (RemoteException e) {
                logger.warn("Cannot retrieve datastores for managedEntity '{}': '{}'", managedEntity.getMOR().getVal(), e.getMessage());
            }
            try {
                for (Network network : hostSystem.getNetworks()) {
                    if (!"".equals(vmNetworks)) {
                        vmNetworks += ", ";
                    }
                    vmNetworks += network.getMOR().getVal();
                }
            } catch (RemoteException e) {
                logger.warn("Cannot retrieve networks for managedEntity '{}': '{}'", managedEntity.getMOR().getVal(), e.getMessage());
            }
        } else {
            VirtualMachine virtualMachine = (VirtualMachine) managedEntity;

            vmPowerState = virtualMachine.getSummary().getRuntime().getPowerState().toString();

            try {
                for (Datastore datastore : virtualMachine.getDatastores()) {
                    if (!"".equals(vmDatastores)) {
                        vmDatastores += ", ";
                    }
                    vmDatastores += datastore.getMOR().getVal();
                }
            } catch (RemoteException e) {
                logger.warn("Cannot retrieve datastores for managedEntity '{}': '{}'", managedEntity.getMOR().getVal(), e.getMessage());
            }
            try {
                for (Network network : virtualMachine.getNetworks()) {
                    if (!"".equals(vmNetworks)) {
                        vmNetworks += ", ";
                    }
                    vmNetworks += network.getMOR().getVal();
                }
            } catch (RemoteException e) {
                logger.warn("Cannot retrieve networks for managedEntity '{}': '{}'", managedEntity.getMOR().getVal(), e.getMessage());
            }

            vmRuntimeInformation = virtualMachine.getRuntime().getHost().getVal();
        }

        RequisitionAsset requisitionAssetHostname = new RequisitionAsset("vmwareManagementServer", m_hostname);
        requisitionNode.putAsset(requisitionAssetHostname);

        RequisitionAsset requisitionAssetType = new RequisitionAsset("vmwareManagedEntityType", (managedEntityType == VMWARE_HOSTSYSTEM ? "HostSystem" : "VirtualMachine"));
        requisitionNode.putAsset(requisitionAssetType);

        RequisitionAsset requisitionAssetId = new RequisitionAsset("vmwareManagedObjectId", managedEntity.getMOR().getVal());
        requisitionNode.putAsset(requisitionAssetId);

        RequisitionAsset requisitionAssetDatastores = new RequisitionAsset("vmwareDatastores", vmDatastores);
        requisitionNode.putAsset(requisitionAssetDatastores);

        RequisitionAsset requisitionAssetNetworks = new RequisitionAsset("vmwareNetworks", vmNetworks);
        requisitionNode.putAsset(requisitionAssetNetworks);

        RequisitionAsset requisitionAssetRuntimeInformation = new RequisitionAsset("vmwareRuntimeInformation", ("".equals(vmRuntimeInformation) ? vmPowerState : vmRuntimeInformation + ", " + vmPowerState));
        requisitionNode.putAsset(requisitionAssetRuntimeInformation);

        return requisitionNode;
    }

    /**
     * Builds the complete requisition object.
     *
     * @return the requisition object
     */
    private Requisition buildVMwareRequisition() {
        VmwareViJavaAccess vmwareViJavaAccess = null;

        // for now, set the foreign source to the specified vcenter host
        m_requisition = new Requisition(m_foreignSource);

        if ((m_username == null || "".equals(m_username)) || (m_password == null || "".equals(m_password))) {
            try {
                vmwareViJavaAccess = new VmwareViJavaAccess(m_hostname);
            } catch (MarshalException e) {
                logger.warn("Error initialising VMware connection to '{}': '{}'", m_hostname, e.getMessage());
                return m_requisition;
            } catch (ValidationException e) {
                logger.warn("Error initialising VMware connection to '{}': '{}'", m_hostname, e.getMessage());
                return m_requisition;
            } catch (IOException e) {
                logger.warn("Error initialising VMware connection to '{}': '{}'", m_hostname, e.getMessage());
                return m_requisition;
            }
        } else {
            vmwareViJavaAccess = new VmwareViJavaAccess(m_hostname, m_username, m_password);
        }

        try {
            vmwareViJavaAccess.connect();
        } catch (MalformedURLException e) {
            logger.warn("Error connecting VMware management server '{}': '{}'", m_hostname, e.getMessage());
            return m_requisition;
        } catch (RemoteException e) {
            logger.warn("Error connecting VMware management server '{}': '{}'", m_hostname, e.getMessage());
            return m_requisition;
        }

        try {
            iterateHostSystems(vmwareViJavaAccess);
            iterateVirtualMachines(vmwareViJavaAccess);
        } catch (RemoteException e) {
            logger.warn("Error retrieving managed objects from VMware management server '{}': '{}'", m_hostname, e.getMessage());
            return m_requisition;
        } finally {
            vmwareViJavaAccess.disconnect();
        }

        return m_requisition;
    }

    /**
     * Checks whether the host system should be imported into the requisition.
     *
     * @param hostSystem the system to check
     * @return true for import, false otherwise
     */
    private boolean checkHostPowerState(HostSystem hostSystem) {
        String powerState = hostSystem.getSummary().runtime.getPowerState().toString();

        if ("poweredOn".equals(powerState) && m_importHostPoweredOn)
            return true;
        if ("poweredOff".equals(powerState) && m_importHostPoweredOff)
            return true;
        if ("standBy".equals(powerState) && m_importHostStandBy)
            return true;
        if ("unknown".equals(powerState) && m_importHostUnknown)
            return true;

        return false;
    }

    /**
     * Checks whether the virtual machine should be imported into the requisition.
     *
     * @param virtualMachine the system to check
     * @return true for import, false otherwise
     */
    private boolean checkVMPowerState(VirtualMachine virtualMachine) {
        String powerState = virtualMachine.getSummary().runtime.getPowerState().toString();

        if ("poweredOn".equals(powerState) && m_importVMPoweredOn)
            return true;
        if ("poweredOff".equals(powerState) && m_importVMPoweredOff)
            return true;
        if ("suspended".equals(powerState) && m_importVMSuspended)
            return true;

        return false;
    }

    /**
     * Iterates through the host systems and adds them to the requisition object.
     *
     * @param vmwareViJavaAccess the access/connection to use
     * @throws RemoteException
     */
    private void iterateHostSystems(VmwareViJavaAccess vmwareViJavaAccess) throws RemoteException {
        ManagedEntity[] hostSystems;

        // search for host systems (esx hosts)
        hostSystems = vmwareViJavaAccess.searchManagedEntities("HostSystem");

        if (hostSystems != null) {

            for (ManagedEntity managedEntity : hostSystems) {
                HostSystem hostSystem = (HostSystem) managedEntity;

                // check for correct key/value-pair
                if (checkHostPowerState(hostSystem) && checkForAttribute(hostSystem)) {
                    logger.debug("Adding Host System '{}'", hostSystem.getName());

                    // iterate over all service console networks and add interface Ip addresses
                    LinkedHashSet<String> ipAddresses = new LinkedHashSet<String>();

                    HostNetworkSystem hostNetworkSystem = hostSystem.getHostNetworkSystem();

                    if (hostNetworkSystem != null) {
                        HostNetworkInfo hostNetworkInfo = hostNetworkSystem.getNetworkInfo();
                        if (hostNetworkInfo != null) {

                            HostVirtualNic[] hostVirtualNics = hostNetworkInfo.getConsoleVnic();
                            if (hostVirtualNics != null) {
                                for (HostVirtualNic hostVirtualNic : hostVirtualNics) {
                                    ipAddresses.add(hostVirtualNic.getSpec().getIp().getIpAddress());
                                }
                            }

                            hostVirtualNics = hostNetworkInfo.getVnic();
                            if (hostVirtualNics != null) {
                                for (HostVirtualNic hostVirtualNic : hostVirtualNics) {
                                    ipAddresses.add(hostVirtualNic.getSpec().getIp().getIpAddress());
                                }
                            }
                        }
                    }

                    // create the new node...
                    RequisitionNode node = createRequisitionNode(ipAddresses, hostSystem, VMWARE_HOSTSYSTEM);

                    // ...and add it to the requisition
                    m_requisition.insertNode(node);
                }
            }
        }
    }

    /**
     * Iterates through the virtual machines and adds them to the requisition object.
     *
     * @param vmwareViJavaAccess the access/connection to use
     * @throws RemoteException
     */
    private void iterateVirtualMachines(VmwareViJavaAccess vmwareViJavaAccess) throws RemoteException {
        ManagedEntity[] virtualMachines;

        // search for all virtual machines
        virtualMachines = vmwareViJavaAccess.searchManagedEntities("VirtualMachine");

        if (virtualMachines != null) {

            // check for correct key/value-pair
            for (ManagedEntity managedEntity : virtualMachines) {
                VirtualMachine virtualMachine = (VirtualMachine) managedEntity;

                // import only when the specified attributes is set
                if (checkVMPowerState(virtualMachine) && checkForAttribute(virtualMachine)) {
                    logger.debug("Adding Virtual Machine '{}'", virtualMachine.getName());

                    LinkedHashSet<String> ipAddresses = new LinkedHashSet<String>();

                    // add the Ip address reported by VMware tools, this should be primary
                    ipAddresses.add(virtualMachine.getGuest().getIpAddress());

                    // if possible, iterate over all virtual networks networks and add interface Ip addresses
                    if (virtualMachine.getGuest().getNet() != null) {
                        for (GuestNicInfo guestNicInfo : virtualMachine.getGuest().getNet()) {
                            if (guestNicInfo.getIpAddress() != null) {
                                for (String ipAddress : guestNicInfo.getIpAddress())
                                    ipAddresses.add(ipAddress);
                            }
                        }
                    }

                    // create the new node...
                    RequisitionNode node = createRequisitionNode(ipAddresses, virtualMachine, VMWARE_VIRTUALMACHINE);

                    // add the operating system
                    if (virtualMachine.getGuest().getGuestFullName() != null) {
                        RequisitionAsset requisitionAsset = new RequisitionAsset("operatingSystem", virtualMachine.getGuest().getGuestFullName());
                        node.putAsset(requisitionAsset);
                    }

                    // ...and add it to the requisition
                    m_requisition.insertNode(node);
                }
            }
        }
    }

    /**
     * Checks whether an attribute/value is defined by a managed entity.
     *
     * @param managedEntity the managed entity to check
     * @return true if present and value is equal, false otherwise
     * @throws RemoteException
     */
    private boolean checkForAttribute(ManagedEntity managedEntity) throws RemoteException {
        String key = m_args.get("key");
        String value = m_args.get("value");

        // if key/value is not set, return true
        if (key == null && value == null)
            return true;

        // if only key or value is set, return false
        if (key == null || value == null)
            return false;

        // get available values
        CustomFieldValue[] values = managedEntity.getCustomValue();
        // get available definitions
        CustomFieldDef[] defs = managedEntity.getAvailableField();

        // now search for the correct key/value pair
        for (int i = 0; defs != null && i < defs.length; i++) {
            if (key.equals(defs[i].getName())) {

                int targetIndex = defs[i].getKey();

                for (int j = 0; j < values.length; j++) {
                    if (targetIndex == values[j].getKey()) {
                        return value.equals(((CustomFieldStringValue) values[j]).value);
                    }
                }
            }
        }

        return false;
    }

    /**
     * {@inheritDoc}
     * <p/>
     * Creates a ByteArrayInputStream implementation of InputStream of the XML
     * marshaled version of the Requisition class. Calling close on this stream
     * is safe.
     */
    @Override
    public InputStream getInputStream() throws IOException {

        InputStream stream = null;

        try {
            Requisition r = buildVMwareRequisition();
            stream = new ByteArrayInputStream(jaxBMarshal(r).getBytes());
        } catch (Throwable e) {
            logger.warn("Problem getting input stream: '{}'", e);
            throw new IOExceptionWithCause("Problem getting input stream: " + e, e);
        }

        return stream;
    }

    /**
     * Utility to marshal the Requisition class into XML.
     *
     * @param r the requisition object
     * @return a String of XML encoding the Requisition class
     * @throws javax.xml.bind.JAXBException
     */
    private String jaxBMarshal(Requisition r) throws JAXBException {
        return JaxbUtils.marshal(r);
    }
}

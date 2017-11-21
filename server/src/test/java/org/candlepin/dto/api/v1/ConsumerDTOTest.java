/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.candlepin.dto.api.v1;

import org.candlepin.dto.AbstractDTOTest;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Test suite for the ConsumerDTO class
 */
public class ConsumerDTOTest extends AbstractDTOTest<ConsumerDTO> {


    protected Map<String, Object> values;
    protected OwnerDTOTest ownerDTOTest = new OwnerDTOTest();
    protected EnvironmentDTOTest environmentDTOTest = new EnvironmentDTOTest();
    protected ConsumerInstalledProductDTOTest cipDTOTest = new ConsumerInstalledProductDTOTest();
    protected CapabilityDTOTest capabilityDTOTest = new CapabilityDTOTest();
    protected HypervisorIdDTOTest hypervisorIdDTOTest = new HypervisorIdDTOTest();
    protected GuestIdDTOTest guestIdDTOTest = new GuestIdDTOTest();

    public ConsumerDTOTest() {
        super(ConsumerDTO.class);

        ConsumerTypeDTO type = new ConsumerTypeDTO();
        type.setId("type_id");
        type.setLabel("type_label");
        type.setManifest(true);

        CertificateDTO cert = new CertificateDTO();
        cert.setId("123");
        cert.setKey("cert_key");
        cert.setCert("cert_cert");
        cert.setSerial(new CertificateSerialDTO());

        Map<String, String> facts = new HashMap<String, String>();
        for (int i = 0; i < 5; ++i) {
            facts.put("fact-" + i, "value-" + i);
        }

        Set<ConsumerInstalledProductDTO> installedProducts =
            new HashSet<ConsumerInstalledProductDTO>();
        for (int i = 0; i < 5; ++i) {
            ConsumerInstalledProductDTO installedProductDTO = cipDTOTest.getPopulatedDTOInstance();
            installedProducts.add(installedProductDTO.setId("cip-" + i));
        }

        Set<CapabilityDTO> capabilityDTOS = new HashSet<CapabilityDTO>();
        for (int i = 0; i < 5; ++i) {
            CapabilityDTO capabilityDTO = capabilityDTOTest.getPopulatedDTOInstance();
            capabilityDTOS.add(capabilityDTO.setId("capability-" + i));
        }

        Set<String> contentTags = new HashSet<String>();
        for (int i = 0; i < 5; ++i) {
            contentTags.add("content-tag-" + i);
        }

        List<GuestIdDTO> guestIdDTOS = new ArrayList<GuestIdDTO>();
        for (int i = 0; i < 5; ++i) {
            GuestIdDTO guestIdDTO = guestIdDTOTest.getPopulatedDTOInstance();
            guestIdDTOS.add(guestIdDTO.setId("guest-Id-" + i));
        }

        this.values = new HashMap<String, Object>();
        this.values.put("Id", "test-id");
        this.values.put("Uuid", "test-uuid");
        this.values.put("Name", "test-name");
        this.values.put("UserName", "test-user-name");
        this.values.put("EntitlementStatus", "test-entitlement-status");
        this.values.put("ServiceLevel", "test-service-level");
        this.values.put("ReleaseVer", "test-release-ver");
        this.values.put("Owner", this.ownerDTOTest.getPopulatedDTOInstance());
        this.values.put("Environment", this.environmentDTOTest.getPopulatedDTOInstance());
        this.values.put("EntitlementCount", 0L);
        this.values.put("Facts", facts);
        this.values.put("LastCheckin", new Date());
        this.values.put("InstalledProducts", installedProducts);
        this.values.put("CanActivate", Boolean.TRUE);
        this.values.put("Capabilities", capabilityDTOS);
        this.values.put("HypervisorId", hypervisorIdDTOTest.getPopulatedDTOInstance());
        this.values.put("ContentTags", contentTags);
        this.values.put("AutoHeal", Boolean.TRUE);
        this.values.put("RecipientOwnerKey", "test-recipient-owner");
        this.values.put("Annotations", "test-annotations");
        this.values.put("ContentAccessMode", "test-content-access-mode");
        this.values.put("ConsumerType", type);
        this.values.put("IdentityCertificate", cert);
        this.values.put("GuestIds", guestIdDTOS);
        this.values.put("Created", new Date());
        this.values.put("Updated", new Date());

        GuestIdDTO guestIdDTO = guestIdDTOTest.getPopulatedDTOInstance();
        guestIdDTO.setGuestId("guest-Id-x");
        this.values.put("addGuestId", guestIdDTO);
        this.values.put("removeGuestId", guestIdDTO.getGuestId());

        ConsumerInstalledProductDTO installedProductDTO = cipDTOTest.getPopulatedDTOInstance();
        installedProductDTO.setProductId("blah");
        this.values.put("addInstalledProduct", installedProductDTO);
        this.values.put("removeInstalledProduct", installedProductDTO.getProductId());

    }

    /**
     * @{inheritDocs}
     */
    @Override
    protected Object getInputValueForMutator(String field) {
        return this.values.get(field);
    }

    /**
     * @{inheritDocs}
     */
    @Override
    protected Object getOutputValueForAccessor(String field, Object input) {
        // Nothing to do here
        return input;
    }
}

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
import org.junit.Test;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ActivationKeyDTOTest extends AbstractDTOTest<ActivationKeyDTO> {

    protected Map<String, Object> values;

    public ActivationKeyDTOTest() {
        super(ActivationKeyDTO.class);

        OwnerDTO owner = new OwnerDTO();
        owner.setId("owner_id");
        owner.setKey("owner_key");
        owner.setDisplayName("owner_name");
        owner.setContentPrefix("content_prefix");
        owner.setDefaultServiceLevel("service_level");
        owner.setLogLevel("log_level");
        owner.setAutobindDisabled(true);
        owner.setContentAccessMode("content_access_mode");
        owner.setContentAccessModeList("content_access_mode_list");

        this.values = new HashMap<String, Object>();
        this.values.put("Id", "test-id");
        this.values.put("Name", "test-name");
        this.values.put("Description", "test-description");
        this.values.put("Owner", owner);
        this.values.put("ReleaseVersion", "test-release-ver");
        this.values.put("ServiceLevel", "test-service-level");
        this.values.put("AutoAttach", true);
        this.values.put("Created", new Date());
        this.values.put("Updated", new Date());

        Set<ActivationKeyDTO.ActivationKeyPoolDTO> pools =
            new HashSet<ActivationKeyDTO.ActivationKeyPoolDTO>();
        ActivationKeyDTO.ActivationKeyPoolDTO pool =
            new ActivationKeyDTO.ActivationKeyPoolDTO("test-id-pool", null);
        pools.add(pool);
        this.values.put("Pools", pools);
        pool = new ActivationKeyDTO.ActivationKeyPoolDTO("test-id-pool2", null);
        this.values.put("removePool", pool.getPoolId());
        this.values.put("addPool", pool);

        Set<String> prods = new HashSet<String>();
        prods.add("test-id-prod");
        this.values.put("ProductIds", prods);

        Set<Map<String, String>> productDTOs = new HashSet<Map<String, String>>();
        Map<String, String> productDTO = new HashMap<String, String>();
        productDTO.put("productId", "test-id-prodDto");
        productDTOs.add(productDTO);
        this.values.put("ProductDTOs", productDTOs);
        this.values.put("removeProductId", "test-id-prodDto");
        this.values.put("addProductId", "test-id-prodDto");

        Set<ActivationKeyDTO.ActivationKeyContentOverrideDTO> overrides =
            new HashSet<ActivationKeyDTO.ActivationKeyContentOverrideDTO>();
        ActivationKeyDTO.ActivationKeyContentOverrideDTO overrideDTO =
            new ActivationKeyDTO.ActivationKeyContentOverrideDTO(
            "test-contentLabel-override",
            "test-name-override",
            "test-value-override");
        overrides.add(overrideDTO);
        this.values.put("ContentOverrides", overrides);
        overrideDTO = new ActivationKeyDTO.ActivationKeyContentOverrideDTO(
            "test-contentLabel-override2",
            "test-name-override2",
            "test-value-override2");
        this.values.put("removeContentOverride", overrideDTO);
        this.values.put("addContentOverride", overrideDTO);
    }

    @Override
    protected Map<String, String> getCollectionMethodsToTest() {
        Map<String, String> collectionMethods = new HashMap<String, String>();
        collectionMethods.put("addContentOverride", "ContentOverrides");
        collectionMethods.put("removeContentOverride", "ContentOverrides");
        collectionMethods.put("addPool", "Pools");
        collectionMethods.put("removePool", "Pools");
        collectionMethods.put("addProductId", "ProductIds");
        collectionMethods.put("removeProductId", "ProductIds");
        return collectionMethods;
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

    @Test
    public void testHasProductWithAbsentProduct() {
        ActivationKeyDTO dto = new ActivationKeyDTO();
        assertFalse(dto.hasProductId("test-id-prod-1"));
    }

    @Test
    public void testHasProductWithPresentProduct() {
        ActivationKeyDTO dto = new ActivationKeyDTO();
        Set<String> products = new HashSet<String>();
        products.add("test-id-prod-2");
        dto.setProductIds(products);

        assertTrue(dto.hasProductId("test-id-prod-2"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testHasProductWithNullInput() {
        ActivationKeyDTO dto = new ActivationKeyDTO();
        dto.hasProductId(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddProductWithEmptyProductId() {
        ActivationKeyDTO dto = new ActivationKeyDTO();

        assertTrue(dto.addProductId(""));
    }

    @Test
    public void testHasPoolWithAbsentPool() {
        ActivationKeyDTO dto = new ActivationKeyDTO();

        assertFalse(dto.hasPool("test-id-pool-1"));
    }

    @Test
    public void testHasPoolWithPresentPool() {
        ActivationKeyDTO dto = new ActivationKeyDTO();
        Set<ActivationKeyDTO.ActivationKeyPoolDTO> pools =
            new HashSet<ActivationKeyDTO.ActivationKeyPoolDTO>();
        pools.add(new ActivationKeyDTO.ActivationKeyPoolDTO("test-id-pool-2", null));
        dto.setPools(pools);

        assertTrue(dto.hasPool("test-id-pool-2"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testHasPoolWithNullInput() {
        ActivationKeyDTO dto = new ActivationKeyDTO();
        dto.hasPool(null);
    }
}

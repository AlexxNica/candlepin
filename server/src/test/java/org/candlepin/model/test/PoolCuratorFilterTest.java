/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
package org.candlepin.model.test;

import static org.junit.Assert.*;

import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolFilterBuilder;
import org.candlepin.model.Product;
import org.candlepin.paging.Page;
import org.candlepin.paging.PageRequest;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.Before;
import org.junit.Test;

import java.util.List;


public class PoolCuratorFilterTest extends DatabaseTestFixture {

    private Owner owner;
    private Product product;
    private Consumer consumer;
    private PageRequest req = new PageRequest();
    private Pool searchPool;

    @Before
    public void setUp() {
        // TODO: remove unused stuff here:
        owner = createOwner();
        ownerCurator.create(owner);

        ConsumerType systemType = new ConsumerType(ConsumerTypeEnum.SYSTEM);
        consumerTypeCurator.create(systemType);

        ConsumerType ueberCertType = new ConsumerType(ConsumerTypeEnum.UEBER_CERT);
        consumerTypeCurator.create(ueberCertType);

        product = TestUtil.createProduct();
        productCurator.create(product);

        consumer = TestUtil.createConsumer(owner);
        consumer.setFact("cpu_cores", "4");
        consumerTypeCurator.create(consumer.getType());
        consumerCurator.create(consumer);

        req = new PageRequest();
        req.setPage(1);
        req.setPerPage(10);
        req.setOrder(PageRequest.Order.ASCENDING);
        req.setSortBy("id");

        searchPool = createSearchPools();
    }

    private Pool createSearchPools() {
        Product searchProduct = new Product("awesomeos-server",
                "Awesome OS Server Premium");
        productCurator.create(searchProduct);
        Pool searchPool = createPoolAndSub(owner, searchProduct, 100L,
                TestUtil.createDate(2005, 3, 2), TestUtil.createDate(2050, 3, 2));
        searchPool.addProvidedProduct(TestUtil.createProvidedProduct("101", "Server Bits"));
        searchPool.addProvidedProduct(TestUtil.createProvidedProduct("202",
                "Containers In This One"));
        poolCurator.create(searchPool);

        // Create another we don't intend to see in the results:
        Product hideProduct = TestUtil.createProduct();
        productCurator.create(hideProduct);
        Pool hidePool = createPoolAndSub(owner, hideProduct, 100L,
                TestUtil.createDate(2005, 3, 2), TestUtil.createDate(2050, 3, 2));
        hidePool.addProvidedProduct(TestUtil.createProvidedProduct("101",
                "Workstation Bits"));
        poolCurator.create(hidePool);

        return searchPool;
    }

    private void searchTest(String searchFor, int expectedResults, String ... expectedIds) {
        PoolFilterBuilder filters = new PoolFilterBuilder();
        filters.addContainsTextFilter(searchFor);
        Page<List<Pool>> page = poolCurator.listAvailableEntitlementPools(
                null, owner, null, null, false, filters,
                req, false);
        List<Pool> results = page.getPageData();
        assertEquals(expectedResults, results.size());
        for (String id : expectedIds) {
            boolean found = false;
            for (Pool p : results) {
                if (p.getId().equals(id)) {
                    found = true;
                    continue;
                }
            }
            assertTrue("Missing expected pool: " + id, found);
        }
    }

    @Test
    public void availablePoolsCanBeFilteredBySkuNameExactMatch() throws Exception {
        searchTest("Awesome OS Server Premium", 1, searchPool.getId());
    }

    @Test
    public void availablePoolsCanBeFilteredBySkuName() throws Exception {
        searchTest("Server Premium", 1, searchPool.getId());
    }

    @Test
    public void availablePoolsCanBeFilteredBySkuNameWildcard() throws Exception {
        searchTest("Ser*emium", 1, searchPool.getId());
        searchTest("Ser*emiumaroni", 0, new String [] {});
        searchTest("*Ser*emium*", 1, searchPool.getId());
        searchTest("Ser**emium", 1, searchPool.getId());
    }

    @Test
    public void availablePoolsCanBeFilteredBySkuNameSingleCharWildcard() throws Exception {
        searchTest("Ser?er P?emium", 1, searchPool.getId());
        searchTest("Ser??? P?emium", 1, searchPool.getId());
    }

    @Test
    public void availablePoolsCanBeFilteredBySku() throws Exception {
        searchTest("os-ser", 1, searchPool.getId());
    }

//    @Test
//    public void availablePoolsCanBeFilteredByProvidedProducts() throws Exception {
//        searchTest("erv???Bi?s", 1, searchPool.getId());
//        searchTest("202", 1, searchPool.getId());
//        searchTest("2?2", 1, searchPool.getId());
//    }

}

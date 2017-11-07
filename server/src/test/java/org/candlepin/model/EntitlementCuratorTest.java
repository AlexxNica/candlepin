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
package org.candlepin.model;

import static org.junit.Assert.*;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import org.candlepin.common.paging.Page;
import org.candlepin.common.paging.PageRequest;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.hamcrest.Matchers;
import org.hibernate.Hibernate;
import org.junit.Before;
import org.junit.Test;



/**
 * EntitlementCuratorTest
 */
public class EntitlementCuratorTest extends DatabaseTestFixture {
    @Inject private OwnerCurator ownerCurator;
    @Inject private ProductCurator productCurator;
    @Inject private PoolCurator poolCurator;
    @Inject private ConsumerCurator consumerCurator;
    @Inject private EntitlementCurator entitlementCurator;
    @Inject private EnvironmentCurator envCurator;


    private Entitlement ent1modif;
    private Entitlement ent2modif;
    private Entitlement secondEntitlement;
    private Entitlement firstEntitlement;
    private EntitlementCertificate firstCertificate;
    private EntitlementCertificate secondCertificate;
    private Owner owner;
    private Consumer consumer;
    private Environment environment;
    private Date futureDate;
    private Date pastDate;
    private Product parentProduct;
    private Product providedProduct1;
    private Product providedProduct2;
    private Product testProduct;

    /**
     * Reproducer of EXTRA Lazy problem. This test is here mainly to demonstrate
     * the problem. The problem is that Pool.entitlements is an extra lazy
     * collection and at the same time it is cascading CREATE.
     * The most important lines in this method are:
     *     (1) ent.getPool().getEntitlements().remove(ent);
     *     (2) Hibernate.initialize(ent.getPool().getEntitlements());
     * The problem is that remove() in (1) will not initialize the collection because
     * it is EXTRA LAZY. Then (2) will initialize without removed entitlement
     * Then when owning consumer c is being deleted, hibernate will recreate
     * the entitlements from the initialized collection Pool.entitlements and
     * subsequent delete of Consumer will cause foreign key exception.
     */
    @Test
    public void removeConsumerWithEntitlements() {
        beginTransaction();
        Consumer c = createConsumer(owner);
        consumerCurator.create(c);
        Product product = TestUtil.createProduct();
        productCurator.create(product);
        Pool p = createPoolAndSub(owner, product, 2L, dateSource.currentDate(), createDate(2020, 1, 1));
        poolCurator.create(p);
        EntitlementCertificate cert = createEntitlementCertificate("keyx", "certificatex");
        Entitlement entitlement = createEntitlement(owner, c, p, cert);
        entitlementCurator.create(entitlement);
        commitTransaction();
        entityManager().clear();

        beginTransaction();
        c = consumerCurator.find(c.getId());
        for (Entitlement ent : c.getEntitlements()) {
            ent.getPool().getEntitlements().remove(ent);
            Hibernate.initialize(ent.getPool().getEntitlements());
        }
        try {
            consumerCurator.delete(c);
            consumerCurator.flush();
        }
        catch (Exception ex) {
            assertEquals(ex.getCause().getCause().getClass(),
                    SQLIntegrityConstraintViolationException.class);
        }
        finally {
            rollbackTransaction();
        }
    }

    @Before
    public void setUp() {
        owner = createOwner();
        ownerCurator.create(owner);

        environment = new Environment("env1", "Env 1", owner);
        envCurator.create(environment);

        consumer = createConsumer(owner);
        consumer.setEnvironment(environment);
        consumerCurator.create(consumer);

        testProduct = TestUtil.createProduct();
        testProduct.setAttribute("variant", "Starter Pack");
        productCurator.create(testProduct);

        Pool firstPool = createPoolAndSub(owner, testProduct, 1L,
            dateSource.currentDate(), createDate(2020, 1, 1));
        firstPool.setAttribute("pool_attr_1", "attr1");
        poolCurator.merge(firstPool);

        firstCertificate = createEntitlementCertificate("key", "certificate");

        firstEntitlement = createEntitlement(owner, consumer, firstPool,
            firstCertificate);
        entitlementCurator.create(firstEntitlement);

        Product product1 = TestUtil.createProduct();
        product1.setAttribute("enabled_consumer_types", "satellite");
        productCurator.create(product1);

        Pool secondPool = createPoolAndSub(owner, product1, 1L,
            dateSource.currentDate(), createDate(2020, 1, 1));
        poolCurator.create(secondPool);

        secondCertificate = createEntitlementCertificate("key", "certificate");

        secondEntitlement = createEntitlement(owner, consumer, secondPool,
            secondCertificate);
        entitlementCurator.create(secondEntitlement);

        futureDate = createDate(2050, 1, 1);
        pastDate = createDate(1998, 1, 1);

        parentProduct = TestUtil.createProduct();
        providedProduct1 = TestUtil.createProduct();
        providedProduct2 = TestUtil.createProduct();
        productCurator.create(parentProduct);
        productCurator.create(providedProduct1);
        productCurator.create(providedProduct2);
    }

    @Test
    public void testCompareTo() {
        Entitlement e1 = TestUtil.createEntitlement();
        Entitlement e2 = TestUtil.createEntitlement(e1.getOwner(), e1.getConsumer(), e1.getPool(), null);
        e2.getCertificates().addAll(e1.getCertificates());

        assertTrue(e1.equals(e2));
        assertEquals(0, e1.compareTo(e2));
    }

    private Date createDate(int year, int month, int day) {
        return TestUtil.createDate(year, month, day);
    }

    @Test
    public void listModifyingExcludesEntitlementThatModifiesItself() {
        Date startDate = createDate(2010, 1, 1);
        Date endDate = createDate(2050, 1, 1);
        Pool testPool = createPoolAndSub(owner, parentProduct, 100L,
            startDate, endDate);

        // Provided product 2 will modify 1, both will be on the pool:
        Content c = new Content("fakecontent", "fakecontent", "facecontent",
                "yum", "RH", "http://", "http://", "x86_64");
        Set<String> modifiedIds = new HashSet<String>();
        modifiedIds.add(providedProduct1.getId());
        c.setModifiedProductIds(modifiedIds);
        contentCurator.create(c);
        providedProduct2.addContent(c);

        // Add some provided products to this pool:
        ProvidedProduct p1 = new ProvidedProduct(providedProduct1.getId(),
            providedProduct1.getName());
        ProvidedProduct p2 = new ProvidedProduct(providedProduct2.getId(),
            providedProduct2.getName());
        testPool.addProvidedProduct(p1);
        testPool.addProvidedProduct(p2);
        poolCurator.create(testPool);

        EntitlementCertificate cert = createEntitlementCertificate("key", "certificate");

        Entitlement ent = createEntitlement(owner, consumer, testPool, cert);
        entitlementCurator.create(ent);

        // The ent we just created should *not* be returned as modifying itself:
        Set<Entitlement> ents = entitlementCurator.listModifying(ent);
        assertEquals(0, ents.size());
    }

    public void prepareEntitlementsForModifying() {
        Content contentPool1 = new Content("fakecontent", "fakecontent",
                "facecontent", "yum", "RH", "http://", "http://",
                "x86_64");
        Content contentPool2 = new Content("fakecontent2", "fakecontent2",
                "facecontent2", "yum", "RH", "http://",
                "http://", "x86_64");

        /**
         * Each of these products are provided by respective Entitlements
         */
        Product providedProductEnt1 = TestUtil.createProduct("ppent1", "ppent1");
        Product providedProductEnt2 = TestUtil.createProduct("ppent2", "ppent2");
        Product providedProductEnt3 = TestUtil.createProduct("ppent3", "ppent3");
        Product providedProductEnt4 = TestUtil.createProduct("ppent4", "ppent4");

        ent1modif = createPool("p1", createDate(1999, 1, 1), createDate(1999, 2, 1), providedProductEnt1);
        ent2modif = createPool("p2", createDate(2000, 4, 4), createDate(2001, 3, 3), providedProductEnt2);

        productCurator.create(providedProductEnt1);
        productCurator.create(providedProductEnt2);
        productCurator.create(providedProductEnt3);
        productCurator.create(providedProductEnt4);

        /**
         * Ent1 and Ent2 entitlements are being modified by contentPool1 and
         * contentPool2
         */
        Set<String> modifiedIds1 = new HashSet<String>();
        Set<String> modifiedIds2 = new HashSet<String>();
        modifiedIds1.add(providedProductEnt1.getId());
        modifiedIds2.add(ent2modif.getProductId());

        /**
         * ContentPool1 modifies Ent1 ContentPool2 modifies Ent2
         */
        contentPool1.setModifiedProductIds(modifiedIds1);
        contentPool2.setModifiedProductIds(modifiedIds2);

        contentCurator.create(contentPool1);
        contentCurator.create(contentPool2);

        /**
         * Ent3 has content 1 and Ent4 has content 2
         */
        providedProductEnt3.addContent(contentPool1);
        providedProductEnt4.addContent(contentPool2);

        createPool("p3", createDate(1998, 1, 1), createDate(2003, 2, 1),
                providedProductEnt3);
        createPool("p4", createDate(2001, 2, 30), createDate(2002, 1, 10),
                providedProductEnt4);

        createPool("p5", createDate(2000, 5, 5), createDate(2000, 5, 10), null);

        createPool("p6", createDate(1998, 1, 1), createDate(1998, 12, 31), null);
        createPool("p7", createDate(2003, 2, 2), createDate(2003, 3, 3), null);
    }

    @Test
    public void getOverlappingForModifying() {
        prepareEntitlementsForModifying();

        ProductEntitlements pents = entitlementCurator.getOverlappingForModifying(
                Arrays.asList(ent1modif, ent2modif));

        assertTrue(!pents.isEmpty());
        assertEquals(9, pents.getAllProductIds().size());
        for (String id : Arrays.asList("p1", "p2", "p3", "p4", "p5", "ppent1",
                "ppent2", "ppent3", "ppent4")) {
            assertTrue(pents.getAllProductIds().contains(id));
        }
    }

    private Entitlement createPool(String id, Date startDate, Date endDate, Product provided) {
        EntitlementCertificate cert = createEntitlementCertificate("key", "certificate");
        Pool p = TestUtil.createPool(owner, TestUtil.createProduct(id, id));

        if (provided != null) {
            p.addProvidedProduct(new ProvidedProduct(provided.getId(), provided.getName()));
        }

        p.setStartDate(startDate);
        p.setEndDate(endDate);
        Entitlement e1 = createEntitlement(owner, consumer, p, cert);
        poolCurator.create(p);
        entitlementCurator.create(e1);

        return e1;
    }

    @Test
    public void batchListModifying() {
        prepareEntitlementsForModifying();
        Set<Entitlement> ents = entitlementCurator.batchListModifying(Arrays.asList(ent1modif, ent2modif));

        assertEquals(2, ents.size());

        for (Entitlement ent : ents) {
            assertTrue(ent.getProductId().equals("p3") || ent.getProductId().equals("p4"));
        }
    }


    private Entitlement setupListProvidingEntitlement() {
        Date startDate = createDate(2000, 1, 1);
        Date endDate = createDate(2005, 1, 1);
        Pool testPool = createPoolAndSub(owner, parentProduct, 1L,
            startDate, endDate);

        // Add some provided products to this pool:
        ProvidedProduct p1 = new ProvidedProduct(providedProduct1.getId(),
            providedProduct1.getName());
        ProvidedProduct p2 = new ProvidedProduct(providedProduct2.getId(),
            providedProduct2.getName());
        testPool.addProvidedProduct(p1);
        testPool.addProvidedProduct(p2);
        poolCurator.create(testPool);

        EntitlementCertificate cert = createEntitlementCertificate("key", "certificate");

        Entitlement ent = createEntitlement(owner, consumer, testPool, cert);
        entitlementCurator.create(ent);

        return ent;
    }

    @Test
    public void listEntitledProductIds() {
        Entitlement ent = setupListProvidingEntitlement();
        Set<String> results = entitlementCurator.listEntitledProductIds(consumer,
                ent.getStartDate(), ent.getEndDate());
        assertEquals(3, results.size());
        assertTrue(results.contains(providedProduct1.getId()));
        assertTrue(results.contains(providedProduct2.getId()));
        assertTrue(results.contains(ent.getPool().getProductId()));
    }

    @Test
    public void listEntitledProductIdsStartDateOverlap() {
        Entitlement ent = setupListProvidingEntitlement();
        Set<String> results = entitlementCurator.listEntitledProductIds(consumer,
            createDate(2002, 1, 1), createDate(2006, 1, 1));
        assertEquals(3, results.size());
        assertTrue(results.contains(ent.getPool().getProductId()));
    }


    @Test
    public void listEntitledProductIdsEndDateOverlap() {
        Entitlement ent = setupListProvidingEntitlement();
        Set<String> results = entitlementCurator.listEntitledProductIds(consumer,
                pastDate, createDate(2002, 1, 1));
        assertEquals(3, results.size());
        assertTrue(results.contains(ent.getPool().getProductId()));
    }

    @Test
    public void listEntitledProductIdsTotalOverlap() {
        Entitlement ent = setupListProvidingEntitlement();
        Set<String> results = entitlementCurator.listEntitledProductIds(consumer,
                pastDate, futureDate);
        // Picks up suite pools as well:
        assertEquals(5, results.size());
        assertTrue(results.contains(ent.getPool().getProductId()));
    }

    @Test
    public void listEntitledProductIdsNoOverlap() {
        setupListProvidingEntitlement();
        Set<String> results = entitlementCurator.listEntitledProductIds(consumer,
                pastDate, pastDate);
        assertEquals(0, results.size());
    }

    @Test
    public void shouldReturnCorrectCertificate() {
        Entitlement e = entitlementCurator
            .findByCertificateSerial(secondCertificate.getSerial().getId());
        assertEquals(secondEntitlement, e);
    }

    @Test
    public void shouldReturnInCorrectCertificate() {
        Entitlement e = entitlementCurator
            .findByCertificateSerial(firstCertificate.getSerial().getId());
        assertNotSame(secondEntitlement, e);
    }

    @Test
    public void listForConsumerOnDate() {
        List<Entitlement> ents = entitlementCurator.listByConsumerAndDate(
            consumer, createDate(2015, 1, 1));
        assertEquals(2, ents.size());
    }

    @Test
    public void listByEnvironment() {
        List<Entitlement> ents = entitlementCurator.listByEnvironment(
            environment);
        assertEquals(2, ents.size());
    }

    @Test
    public void testListByConsumerAndProduct() {
        PageRequest req = new PageRequest();
        req.setPage(1);
        req.setPerPage(10);
        req.setOrder(PageRequest.Order.ASCENDING);
        req.setSortBy("id");

        Product product = TestUtil.createProduct();
        productCurator.create(product);

        Pool pool = createPoolAndSub(owner, product, 1L,
            dateSource.currentDate(), createDate(2020, 1, 1));
        poolCurator.create(pool);

        for (int i = 0; i < 10; i++) {
            EntitlementCertificate cert =
                createEntitlementCertificate("key", "certificate");
            Entitlement ent = createEntitlement(owner, consumer, pool, cert);
            entitlementCurator.create(ent);
        }

        Page<List<Entitlement>> page =
            entitlementCurator.listByConsumerAndProduct(consumer, product.getId(), req);
        assertEquals(Integer.valueOf(10), page.getMaxRecords());

        List<Entitlement> ents = page.getPageData();
        assertEquals(10, ents.size());

        // Make sure we have the real PageRequest, not the dummy one we send in
        // with the order and sortBy fields.
        assertEquals(req, page.getPageRequest());

        // Check that we've sorted ascending on the id
        for (int i = 0; i < ents.size(); i++) {
            if (i < ents.size() - 1) {
                assertTrue(ents.get(i).getId().compareTo(ents.get(i + 1).getId()) < 1);
            }
        }
    }

    @Test
    public void testListByConsumerAndProductWithoutPaging() {
        Product product = TestUtil.createProduct();
        productCurator.create(product);

        Pool pool = createPoolAndSub(owner, product, 1L,
            dateSource.currentDate(), createDate(2020, 1, 1));
        poolCurator.create(pool);

        for (int i = 0; i < 10; i++) {
            EntitlementCertificate cert =
                createEntitlementCertificate("key", "certificate");
            Entitlement ent = createEntitlement(owner, consumer, pool, cert);
            entitlementCurator.create(ent);
        }

        Product product2 = TestUtil.createProduct();
        productCurator.create(product2);

        Pool pool2 = createPoolAndSub(owner, product2, 1L,
            dateSource.currentDate(), createDate(2020, 1, 1));
        poolCurator.create(pool2);

        for (int i = 0; i < 10; i++) {
            EntitlementCertificate cert =
                createEntitlementCertificate("key", "certificate");
            Entitlement ent2 = createEntitlement(owner, consumer, pool2, cert);
            entitlementCurator.create(ent2);
        }

        Page<List<Entitlement>> page =
            entitlementCurator.listByConsumerAndProduct(consumer, product.getId(), null);
        assertEquals(Integer.valueOf(10), page.getMaxRecords());

        List<Entitlement> ents = page.getPageData();
        assertEquals(10, ents.size());

        assertNull(page.getPageRequest());
    }

    @Test
    public void testListByConsumerAndProductFiltered() {
        PageRequest req = new PageRequest();
        req.setPage(1);
        req.setPerPage(10);

        Product product = TestUtil.createProduct();
        productCurator.create(product);

        Pool pool = createPoolAndSub(owner, product, 1L,
            dateSource.currentDate(), createDate(2020, 1, 1));
        poolCurator.create(pool);

        for (int i = 0; i < 5; i++) {
            EntitlementCertificate cert =
                createEntitlementCertificate("key", "certificate");
            Entitlement ent = createEntitlement(owner, consumer, pool, cert);
            entitlementCurator.create(ent);
        }

        Product product2 = TestUtil.createProduct();
        productCurator.create(product2);

        Pool pool2 = createPoolAndSub(owner, product2, 1L,
            dateSource.currentDate(), createDate(2020, 1, 1));
        poolCurator.create(pool2);

        for (int i = 0; i < 5; i++) {
            EntitlementCertificate cert =
                createEntitlementCertificate("key", "certificate");
            Entitlement ent = createEntitlement(owner, consumer, pool2, cert);
            entitlementCurator.create(ent);
        }

        Page<List<Entitlement>> page =
            entitlementCurator.listByConsumerAndProduct(consumer, product.getId(), req);
        assertEquals(Integer.valueOf(5), page.getMaxRecords());
        assertEquals(5, page.getPageData().size());
    }

    @Test
    public void listByConsumerExpired() {
        List<Entitlement> ents = entitlementCurator.listByConsumer(consumer,
            new EntitlementFilterBuilder());
        // Should be 2 entitlements already
        assertEquals(2, ents.size());

        Product product = TestUtil.createProduct();
        productCurator.create(product);
        // expired pool
        Pool pool = createPoolAndSub(owner, product, 1L,
            createDate(2000, 1, 1), createDate(2000, 2, 2));
        poolCurator.create(pool);
        for (int i = 0; i < 2; i++) {
            EntitlementCertificate cert =
                createEntitlementCertificate("key", "certificate");
            Entitlement ent = createEntitlement(owner, consumer, pool, cert);
            entitlementCurator.create(ent);
        }

        // Do not show the expired entitlements, size should be the same as before
        assertEquals(2, ents.size());
    }

    @Test
    public void listByConsumerFilteringByProductAttribute() {
        EntitlementFilterBuilder filters = new EntitlementFilterBuilder();
        filters.addAttributeFilter("variant", "Starter Pack");

        // Should be 2 entitlements already
        List<Entitlement> ents = entitlementCurator.listByConsumer(consumer, filters);
        assertEquals(1, ents.size());

        assertEquals(testProduct.getId(), ents.get(0).getPool().getProductId());
    }

    @Test
    public void listByConsumerFilterByMatches() {
        EntitlementFilterBuilder filters = new EntitlementFilterBuilder();
        filters.addMatchesFilter(testProduct.getName());

        // Should be 2 entitlements already
        List<Entitlement> ents = entitlementCurator.listByConsumer(consumer, filters);
        assertEquals(1, ents.size());
        assertEquals(ents.get(0).getPool().getName(), testProduct.getName());
        assertEquals(ents.get(0).getPool().getProductId(), testProduct.getId());
    }

    @Test
    public void listByConsumersFilteringByPoolAttribute() {
        EntitlementFilterBuilder filters = new EntitlementFilterBuilder();
        filters.addAttributeFilter("pool_attr_1", "attr1");

        // Should be 2 entitlements already
        List<Entitlement> ents = entitlementCurator.listByConsumer(consumer, filters);
        assertEquals(1, ents.size());

        Pool p = ents.get(0).getPool();
        assertTrue("Did not find ent by pool attribute 'pool_attr_1'", p.hasAttribute("pool_attr_1"));
        assertEquals(p.getAttributeValue("pool_attr_1"), "attr1");
    }

    @Test
    public void findByStackIdTest() {
        String stackingId = "test_stack_id";
        Product product = TestUtil.createProduct();
        product.setAttribute("stacking_id", stackingId);
        productCurator.create(product);

        Pool pool = createPoolAndSub(owner, product, 1L,
            dateSource.currentDate(), createDate(2020, 1, 1));
        poolCurator.create(pool);
        Entitlement created = bind(consumer, pool);

        List<Entitlement> results = entitlementCurator.findByStackId(consumer, stackingId);
        assertEquals(1, results.size());
        assertTrue(results.contains(created));
    }

    @Test
    public void findByStackIdMultiTest() {
        String stackingId = "test_stack_id";
        Product product = TestUtil.createProduct();
        product.setAttribute("stacking_id", stackingId);
        productCurator.create(product);

        int ents = 5;
        List<Entitlement> createdEntitlements = new LinkedList<Entitlement>();
        for (int i = 0; i < ents; i++) {
            Pool pool = createPoolAndSub(owner, product, 1L,
                dateSource.currentDate(), createDate(2020, 1, 1));
            poolCurator.create(pool);
            createdEntitlements.add(bind(consumer, pool));
        }

        List<Entitlement> results = entitlementCurator.findByStackId(consumer, stackingId);
        assertEquals(ents, results.size());
        assertTrue(results.containsAll(createdEntitlements) &&
            createdEntitlements.containsAll(results));
    }

    @Test
    public void findByStackIdMultiTestWithDerived() {
        String stackingId = "test_stack_id";
        Product product = TestUtil.createProduct();
        product.setAttribute("stacking_id", stackingId);
        productCurator.create(product);

        Consumer otherConsumer = createConsumer(owner);
        otherConsumer.setEnvironment(environment);
        consumerCurator.create(otherConsumer);

        List<Entitlement> createdEntitlements = new LinkedList<Entitlement>();
        for (int i = 0; i < 5; i++) {
            Pool pool = createPoolAndSub(owner, product, 1L,
                dateSource.currentDate(), createDate(2020, 1, 1));
            if (i < 2) {
                pool.setSourceStack(new SourceStack(otherConsumer, "otherstackid" + i));
            }
            else if (i < 4) {
                pool.setSourceEntitlement(createdEntitlements.get(0));
            }
            poolCurator.create(pool);
            createdEntitlements.add(bind(consumer, pool));
        }

        List<Entitlement> results = entitlementCurator.findByStackId(consumer, stackingId);
        assertEquals(1, results.size());
        assertEquals(createdEntitlements.get(4), results.get(0));
    }

    @Test
    public void findUpstreamEntitlementForStack() {
        String stackingId = "test_stack_id";
        Product product = TestUtil.createProduct();
        product.setAttribute("stacking_id", stackingId);
        productCurator.create(product);

        Pool futurePool = createPoolAndSub(owner, product, 1L,
            createDate(2020, 1, 1), createDate(2021, 1, 1));
        poolCurator.create(futurePool);
        bind(consumer, futurePool);

        Pool currentPool = createPoolAndSub(owner, product, 1L,
            dateSource.currentDate(), createDate(2020, 1, 1));
        poolCurator.create(currentPool);
        bind(consumer, currentPool);

        Pool anotherCurrentPool = createPoolAndSub(owner, product, 1L,
            dateSource.currentDate(), createDate(2020, 1, 1));
        poolCurator.create(anotherCurrentPool);
        bind(consumer, anotherCurrentPool);

        // The future entitlement should have been omitted, and the eldest active
        // entitlement should have been selected:
        Entitlement result = entitlementCurator.findUpstreamEntitlementForStack(
            consumer, stackingId);
        assertNotNull(result);
        assertEquals(currentPool, result.getPool());
    }

    @Test
    public void findUpstreamEntitlementForStackNothingActive() {
        String stackingId = "test_stack_id";
        Product product = TestUtil.createProduct();
        product.setAttribute("stacking_id", stackingId);
        productCurator.create(product);

        Pool futurePool = createPoolAndSub(owner, product, 1L,
            createDate(2020, 1, 1), createDate(2021, 1, 1));
        poolCurator.create(futurePool);
        bind(consumer, futurePool);

        // The future entitlement should have been omitted:
        Entitlement result = entitlementCurator.findUpstreamEntitlementForStack(
            consumer, stackingId);
        assertNull(result);
    }

    @Test
    public void findUpstreamEntitlementForStackNoResults() {
        String stackingId = "test_stack_id";
        Product product = TestUtil.createProduct();
        product.setAttribute("stacking_id", stackingId);
        productCurator.create(product);

        Entitlement result = entitlementCurator.findUpstreamEntitlementForStack(
            consumer, stackingId);
        assertNull(result);
    }

    @Test
    public void findEntitlementsByPoolAttributes() {
        Owner owner1 = createOwner();
        ownerCurator.create(owner1);

        Owner owner2 = createOwner();
        ownerCurator.create(owner2);

        Product product = TestUtil.createProduct();

        Pool p1Attributes = TestUtil.createPool(owner1, product);
        Pool p1NoAttributes = TestUtil.createPool(owner1, product);

        Pool p2Attributes = TestUtil.createPool(owner2, product);
        Pool p2NoAttributes = TestUtil.createPool(owner2, product);
        Pool p2BadAttributes = TestUtil.createPool(owner2, product);

        p1Attributes.addAttribute(new PoolAttribute("x", "true"));
        p2Attributes.addAttribute(new PoolAttribute("x", "true"));
        p2BadAttributes.addAttribute(new PoolAttribute("x", "false"));

        poolCurator.create(p1Attributes);
        poolCurator.create(p1NoAttributes);
        poolCurator.create(p2Attributes);
        poolCurator.create(p2NoAttributes);
        poolCurator.create(p2BadAttributes);

        Entitlement e1 = createEntitlement(owner, consumer, p1Attributes,
            createEntitlementCertificate("key", "certificate"));
        entitlementCurator.create(e1);


        Entitlement e2 = createEntitlement(owner, consumer, p2Attributes,
            createEntitlementCertificate("key", "certificate"));
        entitlementCurator.create(e2);

        Entitlement eBadAttributes = createEntitlement(owner, consumer, p2NoAttributes,
            createEntitlementCertificate("key", "certificate"));
        entitlementCurator.create(eBadAttributes);

        Entitlement eNoAttributes = createEntitlement(owner, consumer, p2BadAttributes,
            createEntitlementCertificate("key", "certificate"));
        entitlementCurator.create(eNoAttributes);

        List<Entitlement> results = entitlementCurator.findByPoolAttribute("x", "true");

        assertThat(results, Matchers.hasItems(e1, e2));
    }

    private Entitlement bind(Consumer consumer, Pool pool) {
        EntitlementCertificate cert =
            createEntitlementCertificate("key", "certificate");
        Entitlement ent = createEntitlement(owner, consumer, pool, cert);
        return entitlementCurator.create(ent);
    }

    protected List<Product> createProducts(Owner owner, int count, String prefix) {
        List<Product> products = new LinkedList<Product>();

        for (int i = 0; i < count; ++i) {
            String id = String.format("%s-%d-%d", prefix, (i + 1), TestUtil.randomInt());
            products.add(this.createProduct(id, id));
        }

        return products;
    }

    protected List<Product> createDependentProducts(Owner owner, int count, String prefix,
        Collection<Product> required) {

        List<Product> products = new LinkedList<Product>();

        for (int i = 0; i < count; ++i) {
            int rand = TestUtil.randomInt(10000);
            String id = String.format("%s-%d-%d", prefix, (i + 1), rand);
            Product product = TestUtil.createProduct(id, id);

            String cid = String.format("%s_content-%d-%d", prefix, (i + 1), rand);
            Content content = this.createContent(cid, cid);

            for (Product rprod : required) {
                content.getModifiedProductIds().add(rprod.getId());
            }

            product.addEnabledContent(content);
            products.add(this.createProduct(product));
        }

        return products;
    }

    protected Pool createPoolWithProducts(Owner owner, String sku, Collection<Product> provided) {
        Product skuProd = this.createProduct(sku, sku);

        Pool pool = this.createPool(owner, skuProd, provided, 1000L, TestUtil.createDate(2000, 1, 1),
            TestUtil.createDate(2100, 1, 1));

        return pool;
    }

    protected void resetDirtyEntitlements(Entitlement... entitlements) {
        for (Entitlement entitlement : entitlements) {
            entitlement.setDirty(false);
            this.entitlementCurator.merge(entitlement);
        }

        this.entitlementCurator.flush();
    }

    @Test
    public void testMarkDependentEntitlementsDirty() {
        try {
            this.beginTransaction();

            Owner owner = this.createOwner("test_owner");
            Consumer consumer = this.createConsumer(owner);

            List<Product> requiredProducts = this.createProducts(owner, 3, "test_req_prod");
            List<Product> dependentProduct1 = this.createDependentProducts(owner, 1, "test_dep_prod_a",
                requiredProducts.subList(0, 1));
            List<Product> dependentProduct2 = this.createDependentProducts(owner, 1, "test_dep_prod_b",
                requiredProducts.subList(1, 2));
            List<Product> dependentProduct3 = this.createDependentProducts(owner, 1, "test_dep_prod_c",
                requiredProducts.subList(2, 3));

            // reqPool1 includes requiredProducts 1 and 2
            // reqPool2 includes requiredProducts 2 and 3
            // reqPool3 includes requiredProducts 1, 2 and 3
            Pool requiredPool1 = this.createPoolWithProducts(owner, "reqPool1",
                requiredProducts.subList(0, 2));
            Pool requiredPool2 = this.createPoolWithProducts(owner, "reqPool2",
                requiredProducts.subList(1, 3));
            Pool requiredPool3 = this.createPoolWithProducts(owner, "reqPool3",
                requiredProducts.subList(0, 3));
            Pool dependentPool1 = this.createPoolWithProducts(owner, "depPool1", dependentProduct1);
            Pool dependentPool2 = this.createPoolWithProducts(owner, "depPool2", dependentProduct2);
            Pool dependentPool3 = this.createPoolWithProducts(owner, "depPool3", dependentProduct3);

            // Bind to requiredPool1
            Entitlement reqPool1Ent = this.bind(consumer, requiredPool1);

            // Consumer has no dependent entitlements (yet), so we should get an output of zero here
            int count = this.entitlementCurator.markDependentEntitlementsDirty(Arrays.asList(reqPool1Ent));
            assertEquals(0, count);

            // Bind to dependentPool3 (which requires reqProd3, that is not yet bound)
            Entitlement depPool3Ent = this.bind(consumer, dependentPool3);

            // We have a dependent entitlement now, but the entitlements are not related, so we should
            // still have zero entitlements affected
            count = this.entitlementCurator.markDependentEntitlementsDirty(Arrays.asList(reqPool1Ent));
            assertEquals(0, count);

            // Bind to depPool2, which requires reqProd2, which we have through reqPool1
            Entitlement depPool2Ent = this.bind(consumer, dependentPool2);

            // We should be marking our depPool2Ent dirty now...
            count = this.entitlementCurator.markDependentEntitlementsDirty(Arrays.asList(reqPool1Ent));
            assertEquals(1, count);

            this.entitlementCurator.refresh(reqPool1Ent, depPool2Ent, depPool3Ent);
            assertFalse(reqPool1Ent.getDirty());
            assertFalse(depPool3Ent.getDirty());
            assertTrue(depPool2Ent.getDirty());

            // Reset entitlements so we don't have any dirty flags already set
            this.resetDirtyEntitlements(reqPool1Ent, depPool3Ent, depPool2Ent);

            // Bind to requiredPool1
            Entitlement reqPool2Ent = this.bind(consumer, requiredPool2);

            // We should now hit both depPool2 and depPool3 since we have reqProd 1, 2 and 3 entitled
            count = this.entitlementCurator
                .markDependentEntitlementsDirty(Arrays.asList(reqPool1Ent, reqPool2Ent));
            assertEquals(2, count);

            this.entitlementCurator.refresh(reqPool1Ent, reqPool2Ent, depPool2Ent, depPool3Ent);
            assertFalse(reqPool1Ent.getDirty());
            assertFalse(reqPool2Ent.getDirty());
            assertTrue(depPool2Ent.getDirty());
            assertTrue(depPool3Ent.getDirty());

            this.resetDirtyEntitlements(reqPool1Ent, reqPool2Ent, depPool2Ent, depPool3Ent);

            // Unbind from reqPool1, which leaves us with reqProd 2 and 3.
            this.entitlementCurator.delete(reqPool1Ent);
            this.entitlementCurator.flush();

            // We should still hit both depPool2 and depPool3 since we still have reqProd 2 and 3 through
            // reqPool2
            count = this.entitlementCurator
                .markDependentEntitlementsDirty(Arrays.asList(reqPool1Ent, reqPool2Ent));
            assertEquals(2, count);

            this.entitlementCurator.refresh(reqPool2Ent, depPool2Ent, depPool3Ent);
            assertFalse(reqPool2Ent.getDirty());
            assertTrue(depPool2Ent.getDirty());
            assertTrue(depPool3Ent.getDirty());
        }
        finally {
            this.commitTransaction();
        }
    }

    @Test
    public void testMarkDependentEntitlementsDirtyDoesNotUpdateUnrelatedEntitlements() {
        Owner owner = this.createOwner("test_owner");
        Consumer consumer = this.createConsumer(owner);

        List<Product> requiredProducts = this.createProducts(owner, 3, "test_req_prod");
        List<Product> dependentProduct1 = this.createDependentProducts(owner, 1, "test_dep_prod_a",
            requiredProducts.subList(0, 1));
        List<Product> dependentProduct2 = this.createDependentProducts(owner, 1, "test_dep_prod_b",
            requiredProducts.subList(1, 2));
        List<Product> dependentProduct3 = this.createDependentProducts(owner, 1, "test_dep_prod_c",
            requiredProducts.subList(2, 3));

        Pool requiredPool1 = this.createPoolWithProducts(owner, "reqPool1", requiredProducts.subList(0, 1));
        Pool requiredPool2 = this.createPoolWithProducts(owner, "reqPool2", requiredProducts.subList(1, 2));
        Pool requiredPool3 = this.createPoolWithProducts(owner, "reqPool3", requiredProducts.subList(2, 3));
        Pool dependentPool1 = this.createPoolWithProducts(owner, "depPool1", dependentProduct1);
        Pool dependentPool2 = this.createPoolWithProducts(owner, "depPool2", dependentProduct2);
        Pool dependentPool3 = this.createPoolWithProducts(owner, "depPool3", dependentProduct3);

        Entitlement reqPool1Ent = this.bind(consumer, requiredPool1);
        Entitlement depPool1Ent = this.bind(consumer, dependentPool1);
        Entitlement depPool2Ent = this.bind(consumer, dependentPool2);
        Entitlement depPool3Ent = this.bind(consumer, dependentPool3);

        int count = this.entitlementCurator.markDependentEntitlementsDirty(Arrays.asList(reqPool1Ent));
        assertEquals(1, count);

        this.entitlementCurator.refresh(reqPool1Ent, depPool1Ent, depPool2Ent, depPool3Ent);
        assertFalse(reqPool1Ent.getDirty());
        assertTrue(depPool1Ent.getDirty());
        assertFalse(depPool2Ent.getDirty());
        assertFalse(depPool3Ent.getDirty());
    }

    @Test
    public void testMarkDependentEntitlementsDirtyDoesNotUpdateOtherConsumerEntitlements() {
        Owner owner = this.createOwner("test_owner");
        Consumer consumer1 = this.createConsumer(owner);
        Consumer consumer2 = this.createConsumer(owner);

        List<Product> requiredProducts = this.createProducts(owner, 3, "test_req_prod");
        List<Product> dependentProduct1 = this.createDependentProducts(owner, 1, "test_dep_prod_a",
            requiredProducts.subList(0, 1));
        List<Product> dependentProduct2 = this.createDependentProducts(owner, 1, "test_dep_prod_b",
            requiredProducts.subList(1, 2));
        List<Product> dependentProduct3 = this.createDependentProducts(owner, 1, "test_dep_prod_c",
            requiredProducts.subList(2, 3));

        Pool requiredPool1 = this.createPoolWithProducts(owner, "reqPool1", requiredProducts.subList(0, 1));
        Pool requiredPool2 = this.createPoolWithProducts(owner, "reqPool2", requiredProducts.subList(1, 2));
        Pool requiredPool3 = this.createPoolWithProducts(owner, "reqPool3", requiredProducts.subList(2, 3));
        Pool dependentPool1 = this.createPoolWithProducts(owner, "depPool1", dependentProduct1);
        Pool dependentPool2 = this.createPoolWithProducts(owner, "depPool2", dependentProduct2);
        Pool dependentPool3 = this.createPoolWithProducts(owner, "depPool3", dependentProduct3);

        Entitlement reqPool1Ent = this.bind(consumer1, requiredPool1);
        Entitlement depPool1EntA = this.bind(consumer1, dependentPool1);
        Entitlement depPool2EntA = this.bind(consumer1, dependentPool2);
        Entitlement depPool3EntA = this.bind(consumer1, dependentPool3);
        Entitlement depPool1EntB = this.bind(consumer2, dependentPool1);
        Entitlement depPool2EntB = this.bind(consumer2, dependentPool2);
        Entitlement depPool3EntB = this.bind(consumer2, dependentPool3);

        int count = this.entitlementCurator.markDependentEntitlementsDirty(Arrays.asList(reqPool1Ent));
        assertEquals(1, count);

        this.entitlementCurator.refresh(reqPool1Ent, depPool1EntA, depPool2EntA, depPool3EntA, depPool1EntB,
            depPool2EntB, depPool3EntB);

        assertFalse(reqPool1Ent.getDirty());
        assertTrue(depPool1EntA.getDirty());
        assertFalse(depPool2EntA.getDirty());
        assertFalse(depPool3EntA.getDirty());
        assertFalse(depPool1EntB.getDirty());
        assertFalse(depPool2EntB.getDirty());
        assertFalse(depPool3EntB.getDirty());
    }

    @Test
    public void testMarkDependentEntitlementsDirtyDoesNotUpdateOtherOwnerEntitlements() {
        Owner owner1 = this.createOwner("test_owner-1");
        Owner owner2 = this.createOwner("test_owner-2");
        Consumer consumer1 = this.createConsumer(owner1);
        Consumer consumer2 = this.createConsumer(owner2);

        List<Product> reqProducts1 = Arrays.asList(
            this.createProduct("req_prod_1a", "req_prod_1a"),
            this.createProduct("req_prod_2a", "req_prod_2a"));

        List<Product> reqProducts2 = Arrays.asList(
            this.createProduct("req_prod_1b", "req_prod_1b"),
            this.createProduct("req_prod_2b", "req_prod_2b"));

        List<Product> dependentProductA = this.createDependentProducts(owner1, 1, "test_dep_prod_a",
            reqProducts1.subList(0, 1));
        List<Product> dependentProductB = this.createDependentProducts(owner2, 1, "test_dep_prod_b",
            reqProducts2.subList(0, 1));

        Pool requiredPool1A = this.createPoolWithProducts(owner1, "reqPool1a", reqProducts1.subList(0, 1));
        Pool requiredPool2A = this.createPoolWithProducts(owner1, "reqPool2a", reqProducts1.subList(1, 2));
        Pool requiredPool1B = this.createPoolWithProducts(owner2, "reqPool1b", reqProducts2.subList(0, 1));
        Pool requiredPool2B = this.createPoolWithProducts(owner2, "reqPool2b", reqProducts2.subList(1, 2));
        Pool dependentPoolA = this.createPoolWithProducts(owner1, "depPool1a", dependentProductA);
        Pool dependentPoolB = this.createPoolWithProducts(owner2, "depPool2b", dependentProductB);

        Entitlement reqPool1AEnt = this.bind(consumer1, requiredPool1A);
        Entitlement reqPool2AEnt = this.bind(consumer1, requiredPool2A);
        Entitlement depPoolAEnt = this.bind(consumer1, dependentPoolA);
        Entitlement reqPool1BEnt = this.bind(consumer2, requiredPool1B);
        Entitlement reqPool2BEnt = this.bind(consumer2, requiredPool2B);
        Entitlement depPoolBEnt = this.bind(consumer2, dependentPoolB);

        int count = this.entitlementCurator.markDependentEntitlementsDirty(
            Arrays.asList(reqPool1AEnt, reqPool2AEnt));
        assertEquals(1, count);

        this.entitlementCurator.refresh(reqPool1AEnt, reqPool2AEnt, depPoolAEnt, reqPool1BEnt, reqPool2BEnt,
            depPoolBEnt);

        assertFalse(reqPool1AEnt.getDirty());
        assertFalse(reqPool2AEnt.getDirty());
        assertTrue(depPoolAEnt.getDirty());
        assertFalse(reqPool1BEnt.getDirty());
        assertFalse(reqPool2BEnt.getDirty());
        assertFalse(depPoolBEnt.getDirty());
    }

    @Test
    public void testMarkDependentEntitlementsDirtyUpdatesMultipleConsumers() {
        Owner owner1 = this.createOwner("test_owner-1");
        Owner owner2 = this.createOwner("test_owner-2");
        Consumer consumer1 = this.createConsumer(owner1);
        Consumer consumer2 = this.createConsumer(owner2);

        List<Product> reqProds1 = Arrays.asList(
            this.createProduct("req_prod_1a", "req_prod_1a"),
            this.createProduct("req_prod_2a", "req_prod_2a"));

        List<Product> reqProds2 = Arrays.asList(
            this.createProduct("req_prod_1b", "req_prod_1b"),
            this.createProduct("req_prod_2b", "req_prod_2b"));

        List<Product> dependentProductA = this.createDependentProducts(owner1, 1, "test_dep_prod_a",
            reqProds1.subList(0, 1));
        List<Product> dependentProductB = this.createDependentProducts(owner2, 1, "test_dep_prod_b",
            reqProds2.subList(0, 1));

        Pool requiredPool1A = this.createPoolWithProducts(owner1, "reqPool1a", reqProds1.subList(0, 1));
        Pool requiredPool2A = this.createPoolWithProducts(owner1, "reqPool2a", reqProds1.subList(1, 2));
        Pool requiredPool1B = this.createPoolWithProducts(owner2, "reqPool1b", reqProds2.subList(0, 1));
        Pool requiredPool2B = this.createPoolWithProducts(owner2, "reqPool2b", reqProds2.subList(1, 2));
        Pool dependentPoolA = this.createPoolWithProducts(owner1, "depPool1a", dependentProductA);
        Pool dependentPoolB = this.createPoolWithProducts(owner2, "depPool2a", dependentProductB);

        Entitlement reqPool1AEnt = this.bind(consumer1, requiredPool1A);
        Entitlement reqPool2AEnt = this.bind(consumer1, requiredPool2A);
        Entitlement depPoolAEnt = this.bind(consumer1, dependentPoolA);
        Entitlement reqPool1BEnt = this.bind(consumer2, requiredPool1B);
        Entitlement reqPool2BEnt = this.bind(consumer2, requiredPool2B);
        Entitlement depPoolBEnt = this.bind(consumer2, dependentPoolB);

        int count = this.entitlementCurator.markDependentEntitlementsDirty(
            Arrays.asList(reqPool1AEnt, reqPool2AEnt, reqPool1BEnt, reqPool2BEnt));
        assertEquals(2, count);

        this.entitlementCurator.refresh(reqPool1AEnt, reqPool2AEnt, depPoolAEnt, reqPool1BEnt, reqPool2BEnt,
            depPoolBEnt);

        assertFalse(reqPool1AEnt.getDirty());
        assertFalse(reqPool2AEnt.getDirty());
        assertTrue(depPoolAEnt.getDirty());
        assertFalse(reqPool1BEnt.getDirty());
        assertFalse(reqPool2BEnt.getDirty());
        assertTrue(depPoolBEnt.getDirty());
    }

    @Test
    public void testMarkDependentEntitlementsDirtyDoesNotRequireAllRequiredProducts() {
        // The "modified products" or "required products" collection on a content is a disjunction,
        // not a conjunction. The presence of any of those products should link two entitlements.

        Owner owner = this.createOwner("test_owner");
        Consumer consumer = this.createConsumer(owner);

        List<Product> requiredProducts = this.createProducts(owner, 3, "test_req_prod");
        List<Product> dependentProduct = this.createDependentProducts(owner, 1, "test_dep_prod_a",
            requiredProducts);

        Pool requiredPool1 = this.createPoolWithProducts(owner, "reqPool1", requiredProducts.subList(0, 1));
        Pool requiredPool2 = this.createPoolWithProducts(owner, "reqPool2", requiredProducts.subList(1, 2));
        Pool requiredPool3 = this.createPoolWithProducts(owner, "reqPool3", requiredProducts.subList(2, 3));
        Pool dependentPool = this.createPoolWithProducts(owner, "depPool1", dependentProduct);

        Entitlement reqPool1Ent = this.bind(consumer, requiredPool2);
        Entitlement depPool1Ent = this.bind(consumer, dependentPool);

        int count = this.entitlementCurator.markDependentEntitlementsDirty(Arrays.asList(reqPool1Ent));
        assertEquals(1, count);

        this.entitlementCurator.refresh(reqPool1Ent, depPool1Ent);
        assertFalse(reqPool1Ent.getDirty());
        assertTrue(depPool1Ent.getDirty());
    }

    @Test
    public void testMarkDependentEntitlementsDirtyDoesNotExamineSkuProducts() {
        Owner owner = this.createOwner("test_owner");
        Consumer consumer = this.createConsumer(owner);

        List<Product> requiredProducts = this.createProducts(owner, 3, "test_req_prod");
        List<Product> providedProducts = this.createProducts(owner, 3, "test_prov_prod");
        List<Product> dependentProduct = this.createDependentProducts(owner, 1, "test_dep_prod_a",
            requiredProducts);

        Pool requiredPool = this.createPool(owner, requiredProducts.get(0), providedProducts, 1000L,
            TestUtil.createDate(2000, 1, 1), TestUtil.createDate(2100, 1, 1));

        Pool dependentPool = this.createPoolWithProducts(owner, "depPool1", dependentProduct);

        Entitlement reqPool1Ent = this.bind(consumer, requiredPool);
        Entitlement depPool1Ent = this.bind(consumer, dependentPool);

        int count = this.entitlementCurator.markDependentEntitlementsDirty(Arrays.asList(reqPool1Ent));
        assertEquals(0, count);

        this.entitlementCurator.refresh(reqPool1Ent, depPool1Ent);
        assertFalse(reqPool1Ent.getDirty());
        assertFalse(depPool1Ent.getDirty());
    }

    @Test
    public void testMarkDependentEntitlementsDirtyDoesNotExamineDerivedProducts() {
        Owner owner = this.createOwner("test_owner");
        Consumer consumer = this.createConsumer(owner);

        List<Product> requiredProducts = this.createProducts(owner, 3, "test_req_prod");
        List<Product> providedProducts = this.createProducts(owner, 3, "test_prov_prod");
        List<Product> dependentProduct = this.createDependentProducts(owner, 1, "test_dep_prod_a",
            requiredProducts);

        Pool requiredPool = this.createPoolWithProducts(owner, "reqPool1", providedProducts);
        requiredPool.setDerivedProductId(requiredProducts.get(0).getId());
        this.poolCurator.merge(requiredPool);

        Pool dependentPool = this.createPoolWithProducts(owner, "depPool1", dependentProduct);

        Entitlement reqPool1Ent = this.bind(consumer, requiredPool);
        Entitlement depPool1Ent = this.bind(consumer, dependentPool);

        int count = this.entitlementCurator.markDependentEntitlementsDirty(Arrays.asList(reqPool1Ent));
        assertEquals(0, count);

        this.entitlementCurator.refresh(reqPool1Ent, depPool1Ent);
        assertFalse(reqPool1Ent.getDirty());
        assertFalse(depPool1Ent.getDirty());
    }

    @Test
    public void testMarkDependentEntitlementsDirtyDoesNotExamineDerivedProvidedProducts() {
        Owner owner = this.createOwner("test_owner");
        Consumer consumer = this.createConsumer(owner);

        List<Product> requiredProducts = this.createProducts(owner, 3, "test_req_prod");
        List<Product> providedProducts = this.createProducts(owner, 3, "test_prov_prod");
        List<Product> dependentProduct = this.createDependentProducts(owner, 1, "test_dep_prod_a",
            requiredProducts);

        Pool requiredPool = this.createPoolWithProducts(owner, "reqPool1", providedProducts);

        Set<DerivedProvidedProduct> dpp = requiredPool.getDerivedProvidedProducts();
        for (Product product : requiredProducts) {
            dpp.add(new DerivedProvidedProduct(product.getId(), product.getName(), requiredPool));
        }
        this.poolCurator.merge(requiredPool);

        Pool dependentPool = this.createPoolWithProducts(owner, "depPool1", dependentProduct);

        Entitlement reqPool1Ent = this.bind(consumer, requiredPool);
        Entitlement depPool1Ent = this.bind(consumer, dependentPool);

        int count = this.entitlementCurator.markDependentEntitlementsDirty(Arrays.asList(reqPool1Ent));
        assertEquals(0, count);

        this.entitlementCurator.refresh(reqPool1Ent, depPool1Ent);
        assertFalse(reqPool1Ent.getDirty());
        assertFalse(depPool1Ent.getDirty());
    }
}

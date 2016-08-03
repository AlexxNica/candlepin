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
package org.candlepin.controller;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalAnswers.*;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

import org.candlepin.model.Content;
import org.candlepin.model.Owner;
import org.candlepin.model.Product;
import org.candlepin.model.dto.ContentData;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;



/**
 * ContentManagerTest
 */
@RunWith(JUnitParamsRunner.class)
public class ContentManagerTest extends DatabaseTestFixture {

    private ContentManager contentManager;
    private EntitlementCertificateGenerator mockEntCertGenerator;
    private ProductManager productManager;

    @Before
    public void setup() throws Exception {
        this.mockEntCertGenerator = mock(EntitlementCertificateGenerator.class);

        this.productManager = new ProductManager(
            this.mockEntCertGenerator, this.ownerContentCurator, this.ownerProductCurator,
            this.productCurator
        );

        this.contentManager = new ContentManager(
            this.contentCurator, this.mockEntCertGenerator, this.ownerContentCurator,
            this.productCurator, this.productManager
        );
    }

    @Test
    public void testCreateContent() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Content content = TestUtil.createContent("c1", "content-1");

        assertNull(this.ownerContentCurator.getContentById(owner, content.getId()));

        Content output = this.contentManager.createContent(content, owner);

        assertEquals(output, this.ownerContentCurator.getContentById(owner, content.getId()));
    }

    @Test
    public void testCreateContentWithDTO() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        ContentData dto = TestUtil.createContentDTO("c1", "content-1");
        dto.setLabel("test-label");
        dto.setType("test-test");
        dto.setVendor("test-vendor");

        assertNull(this.ownerContentCurator.getContentById(owner, dto.getId()));

        Content output = this.contentManager.createContent(dto, owner);

        assertEquals(output, this.ownerContentCurator.getContentById(owner, dto.getId()));
    }

    @Test(expected = IllegalStateException.class)
    public void testCreateContentThatAlreadyExists() {
        Owner owner = this.createOwner("test-owner", "Test Owner");

        Content content1 = TestUtil.createContent("c1", "content-1");
        Content output = this.contentManager.createContent(content1, owner);

        Content content2 = TestUtil.createContent("c1", "content-1");
        this.contentManager.createContent(content2, owner);
    }

    @Test(expected = IllegalStateException.class)
    public void testCreateContentThatAlreadyExistsWithDTO() {
        Owner owner = this.createOwner("test-owner", "Test Owner");

        Content content1 = TestUtil.createContent("c1", "content-1");
        Content output = this.contentManager.createContent(content1, owner);

        ContentData dto = TestUtil.createContentDTO("c1", "content-1");
        dto.setLabel("test-label");
        dto.setType("test-test");
        dto.setVendor("test-vendor");

        this.contentManager.createContent(dto, owner);
    }

    @Test
    public void testCreateContentMergeWithExisting() {
        Owner owner1 = this.createOwner("test-owner-1", "Test Owner 1");
        Owner owner2 = this.createOwner("test-owner-2", "Test Owner 2");

        Content content1 = TestUtil.createContent("c1", "content-1");
        Content content2 = this.createContent("c1", "content-1", owner2);

        Content output = this.contentManager.createContent(content1, owner1);

        assertEquals(output.getUuid(), content2.getUuid());
        assertEquals(output, content2);
        assertTrue(this.ownerContentCurator.isContentMappedToOwner(output, owner1));
        assertTrue(this.ownerContentCurator.isContentMappedToOwner(output, owner2));
    }

    @Test
    public void testCreateContentMergeWithExistingUsingDTO() {
        Owner owner1 = this.createOwner("test-owner-1", "Test Owner 1");
        Owner owner2 = this.createOwner("test-owner-2", "Test Owner 2");

        Content content1 = TestUtil.createContent("c1", "content-1");
        Content content2 = this.createContent("c1", "content-1", owner2);

        Content output = this.contentManager.createContent(content1.toDTO(), owner1);

        assertEquals(content2.getUuid(), output.getUuid());
        assertEquals(content2, output);
        assertTrue(this.ownerContentCurator.isContentMappedToOwner(output, owner1));
        assertTrue(this.ownerContentCurator.isContentMappedToOwner(output, owner2));
    }

    @Test
    public void testUpdateContentNoChange() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Product product = this.createProduct("p1", "product-1", owner);
        Content content = this.createContent("c1", "content-1", owner);
        product.addContent(content, true);
        product = this.productCurator.merge(product);

        Content output = this.contentManager.updateContent(content, content.toDTO(), owner, true);

        assertEquals(output.getUuid(), content.getUuid());
        assertEquals(output, content);

        verifyZeroInteractions(this.mockEntCertGenerator);
    }

    @Test
    @Parameters({"false", "true"})
    public void testUpdateContent(boolean regenCerts) {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Product product = this.createProduct("p1", "product-1", owner);
        Content content = this.createContent("c1", "content-1", owner);
        ContentData update = TestUtil.createContentDTO("c1", "new content name");
        product.addContent(content, true);
        product = this.productCurator.merge(product);

        Content output = this.contentManager.updateContent(content, update, owner, regenCerts);

        assertEquals(output.getUuid(), content.getUuid());
        assertEquals(output.getName(), update.getName());

        if (regenCerts) {
            verify(this.mockEntCertGenerator, times(1)).regenerateCertificatesOf(
                eq(Arrays.asList(owner)), anyCollectionOf(Product.class), anyBoolean()
            );
        }
        else {
            verifyZeroInteractions(this.mockEntCertGenerator);
        }
    }

    @Test
    @Parameters({"false", "true"})
    public void testUpdateContentConvergeWithExisting(boolean regenCerts) {
        Owner owner1 = this.createOwner("test-owner-1", "Test Owner 1");
        Owner owner2 = this.createOwner("test-owner-2", "Test Owner 2");
        Product product = this.createProduct("p1", "product-1", owner1);
        Content content1 = this.createContent("c1", "content-1", owner1);
        Content content2 = this.createContent("c1", "updated content", owner2);
        ContentData update = TestUtil.createContentDTO("c1", "updated content");
        product.addContent(content1, true);
        product = this.productCurator.merge(product);

        assertTrue(this.ownerContentCurator.isContentMappedToOwner(content1, owner1));
        assertFalse(this.ownerContentCurator.isContentMappedToOwner(content2, owner1));
        assertFalse(this.ownerContentCurator.isContentMappedToOwner(content1, owner2));
        assertTrue(this.ownerContentCurator.isContentMappedToOwner(content2, owner2));

        Content output = this.contentManager.updateContent(content1, update, owner1, regenCerts);

        assertEquals(content2.getUuid(), output.getUuid());
        assertFalse(this.ownerContentCurator.isContentMappedToOwner(content1, owner1));
        assertTrue(this.ownerContentCurator.isContentMappedToOwner(content2, owner1));
        assertFalse(this.ownerContentCurator.isContentMappedToOwner(content1, owner2));
        assertTrue(this.ownerContentCurator.isContentMappedToOwner(content2, owner2));

        if (regenCerts) {
            verify(this.mockEntCertGenerator, times(1)).regenerateCertificatesOf(
                eq(Arrays.asList(owner1)), anyCollectionOf(Product.class), anyBoolean()
            );
        }
        else {
            verifyZeroInteractions(this.mockEntCertGenerator);
        }
    }

    @Test
    @Parameters({"false", "true"})
    public void testUpdateContentDivergeFromExisting(boolean regenCerts) {
        Owner owner1 = this.createOwner("test-owner-1", "Test Owner 1");
        Owner owner2 = this.createOwner("test-owner-2", "Test Owner 2");
        Product product = this.createProduct("p1", "product-1", owner1);
        Content content = this.createContent("c1", "content-1", owner1, owner2);
        ContentData update = TestUtil.createContentDTO("c1", "updated content");
        product.addContent(content, true);
        product = this.productCurator.merge(product);

        assertTrue(this.ownerContentCurator.isContentMappedToOwner(content, owner1));
        assertTrue(this.ownerContentCurator.isContentMappedToOwner(content, owner2));

        Content output = this.contentManager.updateContent(content, update, owner1, regenCerts);

        assertNotEquals(output.getUuid(), content.getUuid());
        assertTrue(this.ownerContentCurator.isContentMappedToOwner(output, owner1));
        assertFalse(this.ownerContentCurator.isContentMappedToOwner(output, owner2));
        assertFalse(this.ownerContentCurator.isContentMappedToOwner(content, owner1));
        assertTrue(this.ownerContentCurator.isContentMappedToOwner(content, owner2));

        if (regenCerts) {
            verify(this.mockEntCertGenerator, times(1)).regenerateCertificatesOf(
                eq(Arrays.asList(owner1)), anyCollectionOf(Product.class), anyBoolean()
            );
        }
        else {
            verifyZeroInteractions(this.mockEntCertGenerator);
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testUpdateContentThatDoesntExist() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Content content = TestUtil.createContent("c1", "content-1");
        ContentData update = TestUtil.createContentDTO("c1", "new_name");

        assertFalse(this.ownerContentCurator.isContentMappedToOwner(content, owner));

        this.contentManager.updateContent(content, update, owner, false);
    }

    @Test
    @Parameters({"false", "true"})
    public void testRemoveContent(boolean regenCerts) {
        Owner owner = this.createOwner("test-owner-1", "Test Owner 1");
        Product product = this.createProduct("p1", "product-1", owner);
        Content content = this.createContent("c1", "content-1", owner);
        product.addContent(content, true);
        product = this.productCurator.merge(product);

        assertTrue(this.ownerContentCurator.isContentMappedToOwner(content, owner));

        this.contentManager.removeContent(content, owner, regenCerts);

        assertFalse(this.ownerContentCurator.isContentMappedToOwner(content, owner));
        assertNull(this.contentCurator.find(content.getUuid()));

        if (regenCerts) {
            verify(this.mockEntCertGenerator, times(1)).regenerateCertificatesOf(
                eq(Arrays.asList(owner)), anyCollectionOf(Product.class), anyBoolean()
            );
        }
        else {
            verifyZeroInteractions(this.mockEntCertGenerator);
        }
    }

    @Test
    @Parameters({"false", "true"})
    public void testRemoveContentDivergeFromExisting(boolean regenCerts) {
        Owner owner1 = this.createOwner("test-owner-1", "Test Owner 1");
        Owner owner2 = this.createOwner("test-owner-2", "Test Owner 2");
        Product product = this.createProduct("p1", "product-1", owner1, owner2);
        Content content = this.createContent("c1", "content-1", owner1, owner2);
        product.addContent(content, true);
        product = this.productCurator.merge(product);

        assertTrue(this.ownerContentCurator.isContentMappedToOwner(content, owner1));
        assertTrue(this.ownerContentCurator.isContentMappedToOwner(content, owner2));

        this.contentManager.removeContent(content, owner1, regenCerts);

        assertFalse(this.ownerContentCurator.isContentMappedToOwner(content, owner1));
        assertTrue(this.ownerContentCurator.isContentMappedToOwner(content, owner2));
        assertNotNull(this.contentCurator.find(content.getUuid()));

        if (regenCerts) {
            verify(this.mockEntCertGenerator, times(1)).regenerateCertificatesOf(
                eq(Arrays.asList(owner1)), anyCollectionOf(Product.class), anyBoolean()
            );
        }
        else {
            verifyZeroInteractions(this.mockEntCertGenerator);
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testRemoveContentThatDoesntExist() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Content content = TestUtil.createContent("c1", "content-1");

        assertFalse(this.ownerContentCurator.isContentMappedToOwner(content, owner));

        this.contentManager.removeContent(content, owner, true);
    }


}

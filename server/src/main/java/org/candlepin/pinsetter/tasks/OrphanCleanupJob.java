/**
 * Copyright (c) 2009 - 2016 Red Hat, Inc.
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
package org.candlepin.pinsetter.tasks;

import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.OwnerContentCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.OwnerProductCurator;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.LockModeType;



/**
 * The OrphanCleanupJob searches for orphaned entities (products and content and the time of
 * writing) and removes them.
 */
public class OrphanCleanupJob extends KingpinJob {
    private static Logger log = LoggerFactory.getLogger(OrphanCleanupJob.class);

    // Every Sunday at 3:00am
    public static final String DEFAULT_SCHEDULE = "0 0 3 ? * 1";

    private ContentCurator contentCurator;
    private OwnerContentCurator ownerContentCurator;
    private ProductCurator productCurator;
    private OwnerProductCurator ownerProductCurator;

    @Inject
    public OrphanCleanupJob(ContentCurator contentCurator, OwnerContentCurator ownerContentCurator,
        ProductCurator productCurator, OwnerProductCurator ownerProductCurator) {

        this.ownerContentCurator = ownerContentCurator;
        this.contentCurator = contentCurator;
        this.ownerProductCurator = ownerProductCurator;
        this.productCurator = productCurator;
    }

    @Override
    @Transactional
    public void toExecute(JobExecutionContext ctx) throws JobExecutionException {
        log.debug("Deleting orphaned entities...");
        int count;

        // Content
        count = 0;
        CandlepinQuery<Content> contentQuery = this.ownerContentCurator.getOrphanedContent()
            .setLockMode(LockModeType.PESSIMISTIC_WRITE);

        for (Content content : contentQuery) {
            this.contentCurator.delete(content);
            ++count;
        }

        this.contentCurator.flush();
        log.debug("{} orphaned content entities deleted", count);

        // Products
        count = 0;
        CandlepinQuery<Product> productQuery = this.ownerProductCurator.getOrphanedProducts()
            .setLockMode(LockModeType.PESSIMISTIC_WRITE);

        for (Product product : productQuery) {
            this.productCurator.delete(product);
            ++count;
        }

        this.productCurator.flush();
        log.debug("{} orphaned product entities deleted", count);
    }
}

/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.controller;

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerCurator;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementCurator;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.PoolCurator;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.policy.Enforcer;
import org.fedoraproject.candlepin.policy.ValidationResult;
import org.fedoraproject.candlepin.policy.js.PreEntHelper;

import com.google.inject.Inject;
import com.wideplay.warp.persist.Transactional;

import org.apache.log4j.Logger;

import java.util.Date;

/**
 * Entitler
 */
public class Entitler {
    
    private PoolCurator epCurator;
    private EntitlementCurator entitlementCurator;
    private ConsumerCurator consumerCurator;
    private Enforcer enforcer;
    private static Logger log = Logger.getLogger(Entitler.class);
    
    @Inject
    protected Entitler(PoolCurator epCurator,
            EntitlementCurator entitlementCurator, ConsumerCurator consumerCurator,
            Enforcer enforcer) {
        this.epCurator = epCurator;
        this.entitlementCurator = entitlementCurator;
        this.consumerCurator = consumerCurator;
        this.enforcer = enforcer;
    }

    /**
     * Request an entitlement by product.
     * 
     * If the entitlement cannot be granted, null will be returned.
     * 
     * TODO: Throw exception if entitlement not granted. Report why.
     *
     * @param consumer consumer requesting to be entitled
     * @param product product to be entitled.
     * @return Entitlement
     */
    //
    // NOTE: after calling this method both entitlement pool and consumer parameters
    //       will most certainly be stale. beware!
    //
    @Transactional
    public Entitlement entitle(Consumer consumer, Product product) {
        Owner owner = consumer.getOwner();
        
        // TODO: Don't assume we use the first pool here, once rules have support for 
        // specifying the pool to use. 

        Pool pool = epCurator.listByOwnerAndProduct(owner, product).get(0);
        if (pool == null) {
            throw new RuntimeException("No entitlements for product: " + product.getName());
        }
        
        return addEntitlement(consumer, pool);
    }

    /**
     * Request an entitlement by pool..
     *
     * If the entitlement cannot be granted, null will be returned.
     *
     * TODO: Throw exception if entitlement not granted. Report why.
     *
     * @param consumer consumer requesting to be entitled
     * @param pool entitlement pool to consume from
     * @return Entitlement
     */
    @Transactional
    public Entitlement entitle(Consumer consumer, Pool pool) {
        return addEntitlement(consumer, pool);
    }


    private Entitlement addEntitlement(Consumer consumer, Pool pool) {
        PreEntHelper preHelper = enforcer.pre(consumer, pool);
        ValidationResult result = preHelper.getResult();
        
        if (!result.isSuccessful()) {
            log.warn("Entitlement not granted: " + result.getErrors().toString());
            return null;
        }

        Entitlement e = new Entitlement(pool, consumer, new Date());
        consumer.addEntitlement(e);

        if (preHelper.getGrantFreeEntitlement()) {
            log.info("Granting free entitlement.");
            e.setIsFree(Boolean.TRUE);
        }
        else {
            pool.bumpCurrentMembers();
        }

        enforcer.post(e);
        
        entitlementCurator.create(e);
        consumerCurator.update(consumer);
        epCurator.merge(pool);

        return e;
    }

    // TODO:  Does the enforcer have any rules around removing entitlements?
    @Transactional
    public void revokeEntitlement(Entitlement entitlement) {
        if (!entitlement.isFree()) {
            // put this entitlement back in the pool
            entitlement.getPool().dockCurrentMembers();
        }

        Consumer consumer = entitlement.getConsumer();
        consumer.removeEntitlement(entitlement);

        epCurator.merge(entitlement.getPool());
        entitlementCurator.delete(entitlement);
    }
}

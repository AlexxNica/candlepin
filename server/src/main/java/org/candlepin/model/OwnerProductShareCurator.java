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
package org.candlepin.model;

import com.google.inject.Inject;

import java.util.Collection;
import java.util.Date;
import java.util.List;

import javax.persistence.TypedQuery;

/**
 * Curator to handle creation and maintenance of OwnerProductShare objects
 */
public class OwnerProductShareCurator extends AbstractHibernateCurator<OwnerProductShare> {

    @Inject
    public OwnerProductShareCurator() {
        super(OwnerProductShare.class);
    }

    public List<OwnerProductShare> findProductSharesByRecipient(Owner owner, Collection<String> productIds) {
        String jpql = "FROM OwnerProductShare ps WHERE ps.productId in (:product_ids) " +
            "AND ps.recipientOwner.id = :owner_id";

        TypedQuery<OwnerProductShare> query = getEntityManager()
            .createQuery(jpql, OwnerProductShare.class)
            .setParameter("product_ids", productIds)
            .setParameter("owner_id", owner.getId());

        return query.getResultList();
    }

    public OwnerProductShare testOwnerProductShare(String productId, Owner owner,
        OwnerProduct op, Owner recipientOwner, Date shareDate)
    {
        OwnerProductShare ownerProductShare = new OwnerProductShare();
        ownerProductShare.setProductId(productId);
        ownerProductShare.setSharingOwner(op.getOwner());
        ownerProductShare.setRecipientOwner(recipientOwner);
        ownerProductShare.setProduct(op.getProduct());
        ownerProductShare.setShareDate(shareDate);

        return super.create(ownerProductShare);
    }
}

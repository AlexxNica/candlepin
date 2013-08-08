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
package org.candlepin.resource;

import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang.StringUtils;
import org.candlepin.auth.Principal;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.ContentDeliveryNetwork;
import org.candlepin.model.ContentDeliveryNetworkCurator;
import org.candlepin.model.DistributorVersion;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;

/**
 * DistributorVersionResource
 */
@Path("/content_delivery_network")
public class ContentDeliveryNetworkResource {

    private I18n i18n;
    private ContentDeliveryNetworkCurator curator;

    @Inject
    public ContentDeliveryNetworkResource(I18n i18n,
        ContentDeliveryNetworkCurator curator) {
        this.i18n = i18n;
        this.curator = curator;
    }

    /**
     * @return a ContentDeliveryNetwork list
     * @httpcode 200
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<ContentDeliveryNetwork> getContentDeliveryNetworks() {
        return curator.list();
    }

    /**
     * @httpcode 400
     * @httpcode 404
     * @httpcode 200
     */
    @DELETE
    @Path("/{key}")
    public void delete(@PathParam("key") String key,
        @Context Principal principal) {
        ContentDeliveryNetwork cdn = curator.lookupByKey(key);
        if (cdn != null) {
            curator.delete(cdn);
        }
    }

    /**
     * @return a DistributorVersion
     * @httpcode 200
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ContentDeliveryNetwork create(ContentDeliveryNetwork cdn,
        @Context Principal principal) {
        ContentDeliveryNetwork existing = curator.lookupByKey(cdn.getKey());
        if (existing != null) {
            throw new BadRequestException(
                i18n.tr("A content delivery network with the key {0}" +
                        "already exists", cdn.getKey()));
        }
        return curator.create(cdn);
    }

    /**
     * @return a DistributorVersion
     * @httpcode 200
     */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{key}")
    public ContentDeliveryNetwork update(@PathParam("key") String key,
        ContentDeliveryNetwork cdn,
        @Context Principal principal) {
        ContentDeliveryNetwork existing = verifyAndLookupContentDeliveryNetwork(key);
        if (!StringUtils.isBlank(cdn.getName())) {
            existing.setName(cdn.getName());
        }
        if (!StringUtils.isBlank(cdn.getUrl())) {
            existing.setUrl(cdn.getUrl());
        }
        if (cdn.getCertificate() != null) {
            existing.setCertificate(cdn.getCertificate());
        }
        curator.merge(existing);
        return existing;
    }

    private ContentDeliveryNetwork verifyAndLookupContentDeliveryNetwork(String key) {
        ContentDeliveryNetwork cdn = curator.lookupByKey(key);

        if (cdn == null) {
            throw new NotFoundException(i18n.tr("No such content delivery network: {0}",
                key));
        }
        return cdn;
    }
}

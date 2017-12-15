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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.candlepin.model.Consumer;
import org.candlepin.util.SetView;
import org.candlepin.util.Util;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * A DTO representation of the Entitlement entity
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@ApiModel(parent = TimestampedCandlepinDTO.class, description = "DTO representing an entitlement")
public class EntitlementDTO extends TimestampedCandlepinDTO<EntitlementDTO> implements LinkableDTO {

    private static final long serialVersionUID = 1L;

    private String id;
    private OwnerDTO owner;
    private Consumer consumer; //TODO: Turn into ConsumerDTO once that is introduced
    private PoolDTO pool;
    private Integer quantity;
    private Boolean dirty;
    private Date endDateOverride;
    private Boolean updatedOnStart;
    private Boolean deletedFromPool;
    private Set<CertificateDTO> certificates;

    /**
     * Initializes a new EntitlementDTO instance with null values.
     */
    public EntitlementDTO() {
        // Intentionally left empty
    }

    /**
     * Initializes a new EntitlementDTO instance which is a shallow copy of the provided
     * source entity.
     *
     * @param source
     *  The source entity to copy
     */
    public EntitlementDTO(EntitlementDTO source) {
        super(source);
    }

    /**
     * Retrieves the id field of this EntitlementDTO object.
     *
     * @return the id field of this EntitlementDTO object.
     */
    public String getId() {
        return this.id;
    }

    /**
     * Sets the id to set on this EntitlementDTO object.
     *
     * @param id the id to set on this EntitlementDTO object.
     *
     * @return a reference to this EntitlementDTO object.
     */
    public EntitlementDTO setId(String id) {
        this.id = id;
        return this;
    }

    /**
     * Returns the owner of this entitlement.
     *
     * @return the owner of this entitlement.
     */
    @JsonIgnore
    public OwnerDTO getOwner() {
        return this.owner;
    }

    /**
     * Sets the owner of this entitlement.
     *
     * @param owner the owner to set.
     *
     * @return a reference to this EntitlementDTO object.
     */
    @JsonProperty
    public EntitlementDTO setOwner(OwnerDTO owner) {
        this.owner = owner;
        return this;
    }

    /**
     * Returns the consumer of this entitlement.
     *
     * @return return the associated Consumer.
     */
    public Consumer getConsumer() {
        return consumer;
    }

    /**
     * Associates the given consumer with this entitlement.
     *
     * @param consumer consumer to associate.
     *
     * @return a reference to this EntitlementDTO object.
     */
    public EntitlementDTO setConsumer(Consumer consumer) {
        this.consumer = consumer;
        return this;
    }

    /**
     * Returns the pool of this entitlement.
     *
     * @return the pool of this entitlement.
     */
    public PoolDTO getPool() {
        return this.pool;
    }

    /**
     * Sets the pool of this entitlement.
     *
     * @param pool the pool to set.
     *
     * @return a reference to this EntitlementDTO object.
     */
    public EntitlementDTO setPool(PoolDTO pool) {
        this.pool = pool;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getHref() {
        return "/entitlements/" + getId();
    }

    /**
     * Retrieves the quantity of this entitlement.
     *
     * @return the quantity of this entitlement.
     */
    public Integer getQuantity() {
        return quantity;
    }

    /**
     * Sets the quantity of this entitlement.
     *
     * @param quantity the quantity to set.
     *
     * @return a reference to this EntitlementDTO object.
     */
    public EntitlementDTO setQuantity(Integer quantity) {
        this.quantity = quantity;
        return this;
    }

    /**
     * Returns true if this EntitlementDTO object is dirty, false otherwise.
     *
     * @return true if this EntitlementDTO object is dirty, false otherwise.
     */
    @JsonIgnore
    public Boolean isDirty() {
        return dirty;
    }

    /**
     * Marks this EntitlementDTO object as dirty or not dirty.
     *
     * @param dirty if this EntitlementDTO object is dirty or not.
     *
     * @return a reference to this EntitlementDTO object.
     */
    @JsonProperty
    public EntitlementDTO setDirty(Boolean dirty) {
        this.dirty = dirty;
        return this;
    }

    /**
     *
     * Returns an optional end date override for this entitlement.
     *
     * Typically this is set to null, and the pool's end date is used. In some cases
     * we need to control the expiry of an entitlement separate from the pool.
     *
     * @return optional end date override for this entitlement.
     */
    @JsonIgnore
    public Date getEndDateOverride() {
        return endDateOverride;
    }

    /**
     * Sets an optional end date override for this entitlement.
     *
     * @param endDateOverride an optional end date override for this entitlement.
     *
     * @return a reference to this EntitlementDTO object.
     */
    @JsonProperty
    public EntitlementDTO setEndDateOverride(Date endDateOverride) {
        this.endDateOverride = endDateOverride;
        return this;
    }

    /**
     * Returns true if this entitlement is updated on start, or false otherwise.
     *
     * @return if this entitlement is updated on start or not.
     */
    @JsonIgnore
    public Boolean isUpdatedOnStart() {
        return updatedOnStart;
    }

    /**
     * Sets if this entitlement is updated on start or not.
     *
     * @param updatedOnStart if this entitlement is updated on start or not.
     *
     * @return a reference to this EntitlementDTO object.
     */
    @JsonProperty
    public EntitlementDTO setUpdatedOnStart(Boolean updatedOnStart) {
        this.updatedOnStart = updatedOnStart;
        return this;
    }

    /**
     * Returns true if this entitlement is deleted from the pool, or false otherwise.
     *
     * @return if this entitlement is deleted from the pool or not.
     */
    @JsonIgnore
    public Boolean isDeletedFromPool() {
        return deletedFromPool;
    }

    /**
     * Sets if this entitlement is is deleted from the pool or not.
     *
     * @param deletedFromPool if this entitlement is deleted from the pool or not.
     *
     * @return a reference to this EntitlementDTO object.
     */
    @JsonProperty
    public EntitlementDTO setDeletedFromPool(Boolean deletedFromPool) {
        this.deletedFromPool = deletedFromPool;
        return this;
    }

    /**
     * Retrieves a view of the certificates associated with the entitlement represented by this DTO.
     * If the certificates have not yet been defined, this method returns null.
     * <p></p>
     * Note that the collection returned by this method is a view of the collection backing this
     * set of certificates. Elements cannot be added to the collection, but elements may be removed.
     * Changes made to the collection will be reflected by this certificates data instance.
     *
     * @return
     *  the certificates associated with this key, or null if the certificates have not yet been defined
     */
    public Set<CertificateDTO> getCertificates() {
        return this.certificates != null ? new SetView<CertificateDTO>(this.certificates) : null;
    }

    /**
     * Sets the certificates of the entitlement represented by this DTO.
     *
     * @param certificates
     *  A collection of certificate DTOs to attach to this entitlement DTO, or null to clear the content
     *
     * @throws IllegalArgumentException
     *  if the collection contains null or incomplete certificate DTOs
     *
     * @return
     *  a reference to this EntitlementDTO object.
     */
    public EntitlementDTO setCertificates(Set<CertificateDTO> certificates) {
        if (certificates != null) {
            if (this.certificates == null) {
                this.certificates = new HashSet<CertificateDTO>();
            }
            else {
                this.certificates.clear();
            }

            for (CertificateDTO dto : certificates) {
                if (isNullOrIncomplete(dto)) {
                    throw new IllegalArgumentException("collection contains null " +
                        "or incomplete certificates");
                }
            }
            this.certificates.addAll(certificates);
        }
        else {
            this.certificates = null;
        }
        return this;
    }

    /**
     * Adds the given certificate to this entitlement DTO.
     *
     * @param certificate the certificate DTO to add to this entitlement DTO.
     *
     * @throws IllegalArgumentException
     *  if the certificate is null or incomplete
     *
     * @return true if this certificate was not already contained in this entitlement DTO.
     */
    @JsonIgnore
    public boolean addCertificate(CertificateDTO certificate) {
        if (isNullOrIncomplete(certificate)) {
            throw new IllegalArgumentException("certificate is null or incomplete");
        }

        if (this.certificates == null) {
            this.certificates = new HashSet<CertificateDTO>();
        }

        return this.certificates.add(certificate);
    }

    /*
     * Utility method to validate certificate input.
     */
    private boolean isNullOrIncomplete(CertificateDTO certificate) {
        return certificate == null || certificate.getSerial() == null ||
            certificate.getId() == null || certificate.getId().isEmpty() ||
            certificate.getKey() == null || certificate.getKey().isEmpty() ||
            certificate.getCert() == null || certificate.getCert().isEmpty();
    }

    /**
     * Returns the start date of this entitlement.
     *
     * @return Returns the startDate from the pool of this entitlement.
     */
    public Date getStartDate() {
        if (pool == null) {
            return null;
        }

        return pool.getStartDate();
    }

    /**
     * Returns the end date of this entitlement.
     *
     * @return Returns the endDate. If an override is specified for this entitlement,
     * we return this value. If not we'll use the end date of the pool.
     */
    public Date getEndDate() {
        if (endDateOverride != null) {
            return endDateOverride;
        }

        if (pool == null) {
            return null;
        }

        return pool.getEndDate();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("EntitlementDTO [id: %s, product id: %s, pool id: %s, consumer uuid: %s]",
            this.id,
            this.pool != null ? pool.getProductId() : null,
            this.pool != null ? pool.getId() : null,
            this.consumer != null ? consumer.getUuid() : null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof EntitlementDTO && super.equals(obj)) {
            EntitlementDTO that = (EntitlementDTO) obj;

            // Pull the nested object IDs, as we're not interested in verifying that the objects
            // themselves are equal; just so long as they point to the same object.
            String thisOwnerId = this.getOwner() != null ? this.getOwner().getId() : null;
            String thatOwnerId = that.getOwner() != null ? that.getOwner().getId() : null;

            String thisPoolId = this.getPool() != null ? this.getPool().getId() : null;
            String thatPoolId = that.getPool() != null ? that.getPool().getId() : null;

            String thisConsumerId = this.getConsumer() != null ? this.getConsumer().getUuid() : null;
            String thatConsumerId = that.getConsumer() != null ? that.getConsumer().getUuid() : null;

            EqualsBuilder builder = new EqualsBuilder()
                .append(this.getId(), that.getId())
                .append(thisOwnerId, thatOwnerId)
                .append(thisPoolId, thatPoolId)
                .append(thisConsumerId, thatConsumerId)
                .append(this.getQuantity(), that.getQuantity())
                .append(this.isDirty(), that.isDirty())
                .append(this.getEndDateOverride(), that.getEndDateOverride())
                .append(this.isUpdatedOnStart(), that.isUpdatedOnStart())
                .append(this.isDeletedFromPool(), that.isDeletedFromPool())
                .append(this.getEndDate(), that.getEndDate())
                .append(this.getStartDate(), that.getStartDate());

            // As with many collections here, we need to explicitly check the elements ourselves,
            // since it seems very common for collection implementations to not properly implement
            // .equals
            // Note that we're using the boolean operator here as a shorthand way to skip checks
            // when the equality check has already failed.
            boolean equals = builder.isEquals();

            equals = equals && Util.collectionsAreEqual(this.getCertificates(), that.getCertificates());

            return equals;
        }

        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        int certsHashCode = 0;
        Collection<CertificateDTO> certificateDTOs = this.getCertificates();

        if (certificateDTOs != null) {
            for (CertificateDTO dto : certificateDTOs) {
                certsHashCode = 31 * certsHashCode + (dto != null ? dto.hashCode() : 0);
            }
        }

        HashCodeBuilder builder = new HashCodeBuilder(37, 7)
            .append(super.hashCode())
            .append(this.getId())
            .append(this.getOwner() != null ? this.getOwner().getId() : null)
            .append(this.getPool() != null ? this.getPool().getId() : null)
            .append(this.getConsumer() != null ? this.getConsumer().getUuid() : null)
            .append(this.getQuantity())
            .append(this.isDirty())
            .append(this.getEndDateOverride())
            .append(this.isUpdatedOnStart())
            .append(this.isDeletedFromPool())
            .append(this.getEndDate())
            .append(this.getStartDate())
            .append(certsHashCode);

        return builder.toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntitlementDTO clone() {
        EntitlementDTO copy = (EntitlementDTO) super.clone();

        OwnerDTO owner = this.getOwner();
        copy.owner = owner != null ? owner.clone() : null;
        PoolDTO pool = this.getPool();
        copy.pool = pool != null ? pool.clone() : null;
        Consumer consumer = this.getConsumer();
        //TODO change Consumer to ConsumerDTO.
        copy.consumer = consumer != null ?
            new Consumer(consumer.getName(), consumer.getUsername(),
                consumer.getOwner(), consumer.getType()) : null;
        if (consumer != null) {
            copy.consumer.setUuid(consumer.getUuid());
        }

        copy.certificates = this.getCertificates();

        copy.endDateOverride = this.getEndDateOverride() != null ?
                new Date(this.getEndDateOverride().getTime()) : null;

        return copy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntitlementDTO populate(EntitlementDTO source) {
        super.populate(source);

        this.setId(source.getId())
                .setOwner(source.getOwner())
                .setPool(source.getPool())
                .setConsumer(source.getConsumer())
                .setQuantity(source.getQuantity())
                .setDirty(source.isDirty())
                .setEndDateOverride(source.getEndDateOverride())
                .setUpdatedOnStart(source.isUpdatedOnStart())
                .setDeletedFromPool(source.isDeletedFromPool())
                .setCertificates(source.getCertificates());

        return this;
    }
}

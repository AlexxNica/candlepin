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
package org.fedoraproject.candlepin.model;

import java.util.Formatter;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import org.hibernate.annotations.ForeignKey;
import org.hibernate.annotations.GenericGenerator;

import org.fedoraproject.candlepin.util.Util;

/**
 * Represents the user.
 *
 * A user is more akin to an account within an owner. (i.e. organization)
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "cp_user")
public class User extends AbstractHibernateObject {

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    private String id;

    @ManyToMany(targetEntity = Owner.class)
    @ForeignKey(name = "fk_owner_id",
            inverseName = "fk_user_id")
    @JoinTable(name = "cp_user_owners",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "owner_id"))
    private Set<Owner> owners;

    @Column(nullable = false, unique = true)
    private String username;

    private String hashedPassword;

    private boolean superAdmin;

    public User() {
    }

    public User(Set<Owner> owners, String login, String password) {
        this(owners, login, password, false);
    }

    public User(Set<Owner> owners, String login, String password, boolean superAdmin) {
        this.owners = owners;
        this.username = login;
        this.hashedPassword = Util.hash(password);
        this.superAdmin = superAdmin;
    }

    /**
     * @return the id
     */
    @Override
    public String getId() {
        return id;
    }

    /**
     * @param id the id to set
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * @return the username
     */
    public String getUsername() {
        return username;
    }
    /**
     * @param username the login to set
     */
    public void setUsername(String username) {
        this.username = username;
    }
    /**
     * @return the hashed password
     */
    public String getHashedPassword() {
        return hashedPassword;
    }
    /**
     * @param password the password to set
     */
    public void setPassword(String password) {
        this.hashedPassword = Util.hash(password);
    }
    /**
     * @return the owners
     */
    public Set<Owner> getOwners() {
        return owners;
    }
    /**
     * @param owners the owners to set
     */
    public void setOwners(Set<Owner> owners) {
        this.owners = owners;
    }

    /**
     * @param owner the owner to add to the collection
     */
    public void addOwner(Owner owner) {
        this.owners.add(owner);
    }

    /**
     * @return if the user has the SUPER_ADMIN role
     */
    public boolean isSuperAdmin() {
        return superAdmin;
    }

    /**
     * @param superAdmin if the user should have the SUPER_ADMIN role
     */
    public void setSuperAdmin(boolean superAdmin) {
        this.superAdmin = superAdmin;
    }

    /**
     * Return string representation of the user object
     * @return string representation of the user object
     */
    @Override
    public String toString() {
        return new Formatter().format("User :{login: %s, password: %s}",
                username, hashedPassword).toString();
    }

}

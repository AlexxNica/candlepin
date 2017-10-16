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
package org.candlepin.pki.impl;

import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;

import java.security.Security;

/** When this class is loaded, the Bouncy Castle FIPS provider will be installed into the JVM.  The
 * provider can also be added via a call to a static method if needed in a test for example.
 * */
public class BouncyCastleProviderLoader {
    public static final BouncyCastleFipsProvider BCFIPS = new BouncyCastleFipsProvider();

    static {
        addProvider();
    }

    private BouncyCastleProviderLoader() {
        // static methods only
    }

    public static void addProvider() {
        Security.addProvider(BCFIPS);
    }
}

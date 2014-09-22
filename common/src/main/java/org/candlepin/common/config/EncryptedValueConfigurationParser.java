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
package org.candlepin.common.config;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * EncryptedValueConfigurationParser
 */
public abstract class EncryptedValueConfigurationParser extends ConfigurationParser {

    private static Logger log =
        LoggerFactory.getLogger(EncryptedValueConfigurationParser.class);

    private String passphrase = null;

    public EncryptedValueConfigurationParser() {
        super();
    }

    protected void readSecretFile(String secretFile) {
        log.debug("reading secret file: " +  secretFile);

        BufferedReader in = null;

        try {
            in = new BufferedReader(new FileReader(secretFile));
            StringBuilder tmpPassphrase = new StringBuilder();

            String line = null;
            while ((line = in.readLine()) != null) {
                log.debug("str passphrase: " + line);
                tmpPassphrase.append(line);
            }

            log.debug("tmpPassphrase: " + tmpPassphrase.toString());
            passphrase = tmpPassphrase.toString();
        }
        catch (FileNotFoundException e) {
            log.debug("File not found: " + secretFile);
            passphrase = null;
        }
        catch (IOException e) {
            log.debug("IOException while reading: " + secretFile);
            passphrase = null;
        }
        finally {
            if (in != null) {
                try {
                    in.close();
                }
                catch (IOException ioe) {
                    // just closing
                }
            }
        }

        log.debug("Using katello-passwd passphrase: " + passphrase);
    }

    public Properties parseConfig(Map<String, Object> inputConfiguration) {

        readSecretFile((String) inputConfiguration.get("candlepin.passphrase.path"));

        // pull out properties that we know might be crypted passwords
        // unencrypt them, and update the properties with the new versions
        // do this here so DbBasicAuthConfigParser and JPAConfigParser
        // will do it. Split it to a sub method so sub classes can
        // provide there own implementation of crypt/decrypt
        //
        Properties toReturn = new Properties();
        Properties toDecrypt = super.parseConfig(inputConfiguration);

        if (getEncryptedConfigKeys() != null) {
            for (String encConfigKey : getEncryptedConfigKeys()) {
                String passwordString = toDecrypt.getProperty(encConfigKey);
                if (passwordString != null) {
                    toReturn.setProperty(encConfigKey,
                            decryptValue(passwordString, getPassphrase()));
                }
            }
        }
        return toReturn;
    }

    /* encrypt config value, such as a password */
    public String decryptValue(String toDecrypt, String passphrase) {
        log.info("decrypt called");
        if (!toDecrypt.startsWith("$1$")) {
            // this is not an encrypted password, just return it
            log.debug("this is not an encrypted string");
            return toDecrypt;
        }

        // remove the magic string
        toDecrypt = toDecrypt.substring(3);


        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");

            // NOTE: we are creating a 64byte digest here,
            // but only the first 16 bytes are used as the iv
            String ivString = passphrase + passphrase;
            String iv = DigestUtils.sha256Hex(ivString);
            String passphraseDigest = DigestUtils.sha256Hex(passphrase);

            // FIXME: katello-password creates a 64 byte key, but somewhere
            // it gets truncated to 32 bytes, so we have to do that here.
            SecretKeySpec spec = new SecretKeySpec(Arrays.copyOfRange(
                passphraseDigest.getBytes(), 0, 32), "AES");

            cipher.init(Cipher.DECRYPT_MODE, spec,
                new IvParameterSpec(iv.getBytes(), 0, 16));

            // NOTE: the encrypted password is stored hex base64
            byte[] b64bytes = Base64.decodeBase64(toDecrypt);
            return new String(cipher.doFinal(b64bytes));
        }
        catch (Exception e) {
            log.info("Failure trying to decrypt" + toDecrypt , e);
            throw new RuntimeException(e);
        }
    }

    /**
     * @return the passphrase
     */
    public String getPassphrase() {
        // read /etc/katello/secure/passphrase and use it's contents as
        // passphrase
        log.info("getPassphrase: " + passphrase);
        return passphrase;
    }

    /*
     * returns a Set of config keys that should be decrypted if need be
     */
    public abstract Set<String> getEncryptedConfigKeys();
}

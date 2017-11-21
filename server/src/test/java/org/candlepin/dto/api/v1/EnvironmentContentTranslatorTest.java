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

import static org.junit.Assert.*;

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.model.Content;
import org.candlepin.model.EnvironmentContent;
import org.candlepin.test.TestUtil;

import org.junit.runner.RunWith;

import junitparams.JUnitParamsRunner;

/**
 * Test suite for the EnvironmentContentTranslator class
 */
@RunWith(JUnitParamsRunner.class)
public class EnvironmentContentTranslatorTest extends
    AbstractTranslatorTest<EnvironmentContent, EnvironmentContentDTO, EnvironmentContentTranslator> {

    protected ContentTranslator contentTranslator = new ContentTranslator();
    protected EnvironmentContentTranslator ecTranslator = new EnvironmentContentTranslator();

    protected ContentTranslatorTest contentTranslatorTest = new ContentTranslatorTest();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        modelTranslator.registerTranslator(this.contentTranslator, Content.class, ContentDTO.class);
        modelTranslator.registerTranslator(this.ecTranslator, EnvironmentContent.class,
            EnvironmentContentDTO.class);
    }

    @Override
    protected EnvironmentContentTranslator initObjectTranslator() {
        return this.ecTranslator;
    }

    @Override
    protected EnvironmentContent initSourceObject() {
        EnvironmentContent source = new EnvironmentContent();

        source.setId("test_id");
        source.setEnabled(true);

        Content content = TestUtil.createContent("content-1");
        content.setUuid(content.getId() + "_uuid");
        source.setContent(content);

        return source;
    }

    @Override
    protected EnvironmentContentDTO initDestinationObject() {
        // Nothing fancy to do here.
        return new EnvironmentContentDTO();
    }

    @Override
    protected void verifyOutput(EnvironmentContent source, EnvironmentContentDTO dto,
        boolean childrenGenerated) {
        if (source != null) {
            assertEquals(source.getId(), dto.getId());
            assertEquals(source.getEnabled(), dto.isEnabled());

            if (childrenGenerated) {
                assertNotNull(dto.getContent());

                Content content = source.getContent();
                ContentDTO cdto = dto.getContent();

                assertNotNull(cdto);
                assertNotNull(cdto.getUuid());

                if (cdto.getUuid().equals(content.getUuid())) {
                    // Pass the content off to the ContentTranslatorTest to verify it
                    this.contentTranslatorTest.verifyOutput(content, cdto, childrenGenerated);
                }
            }
            else {
                assertNull(dto.getContent());
            }

        }
    }
}

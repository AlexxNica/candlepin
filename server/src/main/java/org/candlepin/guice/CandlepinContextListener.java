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
package org.candlepin.guice;

import org.candlepin.audit.AMQPBusPublisher;
import org.candlepin.audit.HornetqContextListener;
import org.candlepin.common.config.Configuration;
import org.candlepin.common.config.ConfigurationException;
import org.candlepin.common.config.MapConfiguration;
import org.candlepin.common.config.PropertiesFileConfiguration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.logging.LoggerContextListener;
import org.candlepin.pinsetter.core.PinsetterContextListener;
import org.candlepin.util.Util;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Stage;
import com.google.inject.util.Modules;

import org.hibernate.cfg.beanvalidation.BeanValidationEventListener;
import org.hibernate.ejb.HibernateEntityManagerFactory;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.internal.SessionFactoryImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18nManager;

import java.io.File;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import javax.persistence.EntityManagerFactory;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;

/**
 * Customized Candlepin version of
 * {@link GuiceResteasyBootstrapServletContextListener}.
 *
 * The base version pulls in Guice modules by class name from web.xml and
 * instantiates them - however we have a need to add in modules
 * programmatically for, e.g., servlet filters and the wideplay JPA module.
 * This context listener overrides some of the module initialization code to
 * allow for module specification beyond simply listing class names.
 */
public class CandlepinContextListener extends CandlepinGuiceResteasyBootstrap {
    public static final String CONFIGURATION_NAME = Configuration.class.getName();

    private HornetqContextListener hornetqListener;
    private PinsetterContextListener pinsetterListener;
    private LoggerContextListener loggerListener;

    private Injector injector;
    // a bit of application-initialization code. Not sure if this is the
    // best spot for it.
    static {
        I18nManager.getInstance().setDefaultLocale(Locale.US);
    }

    private static Logger log = LoggerFactory.getLogger(CandlepinContextListener.class);
    private Configuration config;

    // getServletContext() from the GuiceServletContextListener is deprecated.
    // See
    // https://github.com/google/guice/blob/bf0e7ce902dd97e62ef16679c587d78d59200450
    // /extensions/servlet/src/com/google/inject/servlet/GuiceServletContextListener.java#L43-L45
    // A typical way of doing this then is to cache the context ourselves:
    // https://github.com/google/guice/issues/603
    // Currently only needed for access to the Configuration.
    private ServletContext servletContext;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        log.info("Candlepin initializing context.");
        I18nManager.getInstance().setDefaultLocale(Locale.US);
        servletContext = sce.getServletContext();

        try {
            log.info("Candlepin reading configuration.");
            config = readConfiguration(servletContext);
        }
        catch (ConfigurationException e) {
            log.error("Could not read configuration file.  Aborting initialization.", e);
            throw new RuntimeException(e);
        }

        log.debug("Candlepin stored config on context.");

        servletContext.setAttribute(CONFIGURATION_NAME, config);

        // set things up BEFORE calling the super class' initialize method.
        super.contextInitialized(sce);

        // Must call super.contextInitialized() before accessing injector
        insertValidationEventListeners(injector);
        hornetqListener = injector.getInstance(HornetqContextListener.class);
        hornetqListener.contextInitialized(injector);
        pinsetterListener = injector.getInstance(PinsetterContextListener.class);
        pinsetterListener.contextInitialized();

        log.info("Candlepin context initialized.");
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        super.contextDestroyed(event);
        hornetqListener.contextDestroyed();
        pinsetterListener.contextDestroyed();
        loggerListener = injector.getInstance(LoggerContextListener.class);
        loggerListener.contextDestroyed();

        // if amqp is enabled, close all connections.
        Configuration config = injector.getInstance(Configuration.class);
        if (config.getBoolean(ConfigProperties.AMQP_INTEGRATION_ENABLED)) {
            Util.closeSafely(injector.getInstance(AMQPBusPublisher.class),
                "AMQPBusPublisher");
            Util.closeSafely(injector.getInstance(AMQPBusPubProvider.class),
                "AMQPBusPubProvider");
        }
    }

    @Override
    protected Injector getInjector(Stage stage, List<Module> modules) {
        return Guice.createInjector(stage, modules);
    }

    protected Configuration readConfiguration(ServletContext context)
        throws ConfigurationException {

        // Use StandardCharsets.UTF_8 when we move to Java 7
        Charset utf8 = Charset.forName("UTF-8");
        PropertiesFileConfiguration systemConfig = new PropertiesFileConfiguration();
        systemConfig.setEncoding(utf8);
        File configFile = new File(ConfigProperties.DEFAULT_CONFIG_FILE);

        if (configFile.canRead()) {
            log.debug("Loading system configuration");
            // First, read the system configuration
            systemConfig.load(configFile);
            log.debug("System configuration: " + systemConfig);
        }

        // load the defaults
        MapConfiguration defaults = new MapConfiguration(
            ConfigProperties.DEFAULT_PROPERTIES);

        log.debug("Loading default configuration values");

        // merge the defaults with the system configuration. ORDER MATTERS.
        // system config must be read FIRST otherwise settings won't be applied.

        // merge does NOT affect systemConfig, it just returns a new object
        // not sure I like that.
        return systemConfig.merge(defaults);
    }

    @Override
    protected Stage getStage(ServletContext context) {
        // RESTEasy 3.0 has a getState with a context that we can override.
        // Right now we don't use context for our need but when we do switch
        // we'll be able to add an @Override to this method.

        // see https://github.com/google/guice/wiki/Bootstrap for information
        // on Stage.
        return Stage.PRODUCTION;
    }

    /**
     * Returns a list of Guice modules to initialize.
     * @return a list of Guice modules to initialize.
     */
    @Override
    protected List<Module> getModules(ServletContext context) {
        List<Module> modules = new LinkedList<Module>();

        modules.add(Modules.override(new DefaultConfig()).with(
                new CustomizableModules().load(config)));

        modules.add(new AbstractModule() {

            @Override
            protected void configure() {
                bind(Configuration.class).toInstance(config);
            }
        });

        modules.add(new CandlepinModule(config));
        modules.add(new CandlepinFilterModule());

        return modules;
    }

    /**
     * There's no way to really get Guice to perform injections on stuff that
     * the JpaPersistModule is creating, so we resort to grabbing the EntityManagerFactory
     * after the fact and adding the Validation EventListener ourselves.
     * @param injector
     */
    private void insertValidationEventListeners(Injector injector) {
        javax.inject.Provider<EntityManagerFactory> emfProvider =
            injector.getProvider(EntityManagerFactory.class);
        HibernateEntityManagerFactory hibernateEntityManagerFactory =
            (HibernateEntityManagerFactory) emfProvider.get();
        SessionFactoryImpl sessionFactoryImpl =
            (SessionFactoryImpl) hibernateEntityManagerFactory.getSessionFactory();
        EventListenerRegistry registry =
            sessionFactoryImpl.getServiceRegistry().getService(EventListenerRegistry.class);

        javax.inject.Provider<BeanValidationEventListener> listenerProvider =
            injector.getProvider(BeanValidationEventListener.class);
        registry.getEventListenerGroup(EventType.PRE_INSERT).appendListener(listenerProvider.get());
        registry.getEventListenerGroup(EventType.PRE_UPDATE).appendListener(listenerProvider.get());
        registry.getEventListenerGroup(EventType.PRE_DELETE).appendListener(listenerProvider.get());
    }

    protected void processInjector(ServletContext context, Injector inj) {
        injector = inj;
        super.processInjector(context, injector);
    }
}

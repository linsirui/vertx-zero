package io.vertx.up;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.up.annotations.Up;
import io.vertx.up.eon.Info;
import io.vertx.up.eon.em.ServerType;
import io.vertx.up.exception.UpClassArgsException;
import io.vertx.up.exception.UpClassInvalidException;
import io.vertx.up.rs.Extractor;
import io.vertx.up.rs.config.AgentExtractor;
import io.vertx.up.rs.config.WorkerExtractor;
import io.vertx.up.web.ZeroAnno;
import io.vertx.up.web.ZeroHttpAgent;
import io.vertx.up.web.ZeroLauncher;
import io.vertx.zero.func.HBool;
import io.vertx.zero.func.HMap;
import io.vertx.zero.func.HTry;
import io.vertx.zero.log.Annal;
import io.vertx.zero.tool.Statute;
import io.vertx.zero.tool.mirror.Anno;
import io.vertx.zero.tool.mirror.Instance;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static io.vertx.up.eon.Info.VTC_END;

/**
 * Vertx Application start information
 */
public class VertxApplication {

    private static final Annal LOGGER = Annal.get(VertxApplication.class);

    private static final Class<?>[] DEFAULT_AGENTS = new Class<?>[]{
            ZeroHttpAgent.class
    };

    private static final ConcurrentMap<ServerType, Class<?>> INTERNALS
            = new ConcurrentHashMap<ServerType, Class<?>>() {
        {
            put(ServerType.HTTP, ZeroHttpAgent.class);
        }
    };

    private transient final Class<?> clazz;
    private transient final VertxPlugin plugin = Instance.singleton(VertxPlugin.class);
    private ConcurrentMap<String, Annotation> annotationMap = new ConcurrentHashMap<>();

    private VertxApplication(final Class<?> clazz) {
        // Must not null
        HBool.execUp(
                null == clazz,
                LOGGER,
                UpClassArgsException.class, getClass());
        this.clazz = clazz;
        this.annotationMap = Anno.get(clazz);
        // Must be invalid
        HBool.execUp(
                !this.annotationMap.containsKey(Up.class.getName()),
                LOGGER,
                UpClassInvalidException.class, getClass(), clazz.getName());
    }

    public static void run(final Class<?> clazz, final Object... args) {
        HTry.execUp(() -> {
            // Run vertx application.
            new VertxApplication(clazz).run(args);
        }, LOGGER);
    }

    public void run(final Object... args) {
        final Launcher launcher = Instance.singleton(ZeroLauncher.class);
        launcher.start(vertx -> {
            /** 1.Find Agent for deploy **/
            deployAgents(vertx);
            /** 2.Find Worker for deploy **/
            deployWorkers(vertx);
            /** 3.Initialize Plugin **/
            this.plugin.connect(vertx);
            /** 4.Connect and started **/
        });
    }

    private void deployAgents(final Vertx vertx) {
        /** 1.Find Agent for deploy **/
        final ConcurrentMap<ServerType, Class<?>> agents
                = getAgents();
        final Extractor<DeploymentOptions> extractor =
                Instance.singleton(AgentExtractor.class);

        HMap.exec(agents, (type, clazz) -> {
            // 2.1 Agent deployment options
            final DeploymentOptions option = extractor.extract(clazz);
            // 2.2 Agent deployment
            deployVerticle(vertx, clazz, option);
        });
    }

    private void deployWorkers(final Vertx vertx) {
        /** 1.Find Workers for deploy **/
        final Set<Class<?>> workers = ZeroAnno.getWorkers();
        final Extractor<DeploymentOptions> extractor =
                Instance.singleton(WorkerExtractor.class);

        for (final Class<?> worker : workers) {
            // 2.1 Worker deployment options
            final DeploymentOptions option = extractor.extract(worker);
            // 2.2 Worker deployment
            deployVerticle(vertx, worker, option);
        }
    }

    private void deployVerticle(final Vertx vertx, final Class<?> clazz,
                                final DeploymentOptions option) {
        // Verticle deployment
        final String name = clazz.getName();
        final String flag = option.isWorker() ? "Worker" : "Agent";
        vertx.deployVerticle(name, option, (result) -> {
            // Success or Failed.
            if (result.succeeded()) {
                LOGGER.info(VTC_END,
                        name, option.getInstances(), result.result(),
                        flag);
            } else {
                LOGGER.info(Info.VTC_FAIL,
                        name, option.getInstances(), result.result(),
                        null == result.cause() ? null : result.cause().getMessage(), flag);
            }
        });
    }

    private ConcurrentMap<ServerType, List<Class<?>>> getMergedAgents() {
        final ConcurrentMap<ServerType, List<Class<?>>> agents = ZeroAnno.getAgents();
        if (agents.isEmpty()) {
            // Inject Http
            final List<Class<?>> internals = new ArrayList<Class<?>>() {
                {
                    add(INTERNALS.get(ServerType.HTTP));
                }
            };
            agents.put(ServerType.HTTP, internals);
        }
        return agents;
    }

    /**
     * Find agent for each server type.
     *
     * @return
     */
    private ConcurrentMap<ServerType, Class<?>> getAgents() {
        final ConcurrentMap<ServerType, List<Class<?>>> agents =
                getMergedAgents();
        final ConcurrentMap<ServerType, Boolean> defines =
                ZeroAnno.isDefined(agents, DEFAULT_AGENTS);
        final ConcurrentMap<ServerType, Class<?>> ret =
                new ConcurrentHashMap<>();
        // Fix Boot
        // 1. If defined, use default
        HMap.exec(agents, (type, list) -> {
            // 2. Defined -> You have defined
            HBool.exec(defines.containsKey(type) && defines.get(type),
                    () -> {

                        // Use user-defined Agent instead.
                        final Class<?> found = Statute.findUnique(list,
                                (item) -> INTERNALS.get(type) != item);
                        if (null != found) {
                            LOGGER.info(Info.AGENT_DEFINED, found.getName(), type);
                            ret.put(type, found);
                        }
                        return null;
                    }, () -> {

                        // Use internal defined ( system defaults )
                        final Class<?> found = Statute.findUnique(list,
                                (item) -> INTERNALS.get(type) == item);
                        if (null != found) {
                            ret.put(type, found);
                        }
                        return null;
                    });
        });
        return ret;
    }
}
package io.vertx.up.rs.router;

import io.vertx.exception.up.EventActionNoneException;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.up.ce.Event;
import io.vertx.up.rs.Aim;
import io.vertx.up.rs.Axis;
import io.vertx.up.rs.Splitter;
import io.vertx.up.rs.hunt.ModeSplitter;
import io.vertx.zero.web.ZeroAnno;
import org.vie.fun.HBool;
import org.vie.util.Instance;
import org.vie.util.log.Annal;

import java.lang.reflect.Method;
import java.util.Set;

public class EventAxis implements Axis {
    private static final Annal LOGGER = Annal.get(EventAxis.class);
    /**
     * Extract all events that will be generated route.
     */
    private static final Set<Event> EVENTS =
            ZeroAnno.getEvents();
    private transient final Splitter splitter = Instance.singleton(ModeSplitter.class);

    @Override
    public void mount(final Router router) {
        // Extract Event foreach
        EVENTS.forEach(event -> {
            // Build Route and connect to each Action
            HBool.exec(null == event, LOGGER,
                    () -> {
                        LOGGER.warn(Info.NULL_EVENT, getClass().getName());
                    },
                    () -> {
                        // 1. Verify
                        this.verify(event);

                        final Route route = router.route();
                        // 2. Path, Method, Order
                        Hub hub = Instance.singleton(UriHub.class);
                        hub.mount(route, event);
                        // 3. Consumes/Produces
                        hub = Instance.singleton(MediaHub.class);
                        hub.mount(route, event);

                        final Aim aim = this.splitter.distribute(event);
                        // 4. Inject handler, event dispatch
                        route.handler(aim.attack(event));
                    });
        });
    }

    private void verify(final Event event) {
        final Method method = event.getAction();
        HBool.execUp(null == method, LOGGER, EventActionNoneException.class,
                getClass(), event);
    }

}
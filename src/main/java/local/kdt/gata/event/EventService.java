package local.kdt.gata.event;

import com.google.common.eventbus.EventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class EventService {

    private static final Logger LOG = LoggerFactory.getLogger(EventService.class);

    private EventBus eventBus;

    public EventService() {
        eventBus = new EventBus();
    }

    public void post(Object event) {
        eventBus.post(event);
    }

    public void register(Object listener) {
        eventBus.register(listener);
    }

    public EventBus getEventBus() {
        return eventBus;
    }
}


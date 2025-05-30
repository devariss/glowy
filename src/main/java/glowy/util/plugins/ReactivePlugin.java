package glowy.util.plugins;

import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.java.JavaPlugin;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.util.Optional;
import java.util.concurrent.Executors;

public abstract class ReactivePlugin extends JavaPlugin {
    private Disposable initDisposable;

    @Override
    public final void onEnable() {
        initDisposable = onInit()
            .subscribeOn(Schedulers.fromExecutor(Executors.newVirtualThreadPerTaskExecutor()))
            .subscribe();
    }

    @Override
    public final void onDisable() {
        Optional.ofNullable(initDisposable)
            .ifPresent(Disposable::dispose);
    }

    protected final <T extends Event> Flux<T> on(Class<T> clazz) {
        return on(clazz, EventPriority.NORMAL, true);
    }

    protected final <T extends Event> Flux<T> on(Class<T> clazz, EventPriority priority, boolean ignoreCancelled) {
        Sinks.Many<T> eventSink = Sinks.many().multicast().onBackpressureBuffer();
        EventExecutor emitEvent = (listener, event) -> eventSink.tryEmitNext(clazz.cast(event));
        getServer().getPluginManager().registerEvent(clazz, new Listener(){}, priority, emitEvent, this, ignoreCancelled);
        return eventSink.asFlux();
    }

    protected abstract Mono<Void> onInit();
}

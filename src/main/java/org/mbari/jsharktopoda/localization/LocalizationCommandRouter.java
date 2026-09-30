package org.mbari.jsharktopoda.localization;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import org.mbari.vcr4j.remote.control.commands.localization.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * Routes localization commands received by vcr4j to the {@link LocalizationTarget} of the
 * addressed video. vcr4j has already replied to the remote app; this only applies the change.
 */
public class LocalizationCommandRouter implements AutoCloseable {

    private static final System.Logger log = System.getLogger(LocalizationCommandRouter.class.getName());

    private final Function<UUID, Optional<LocalizationTarget>> lookup;
    private final Executor executor;
    private final Disposable subscription;

    public LocalizationCommandRouter(Observable<LocalizationsCmd<?, ?>> commands,
                                     Function<UUID, Optional<LocalizationTarget>> lookup,
                                     Executor executor) {
        this.lookup = lookup;
        this.executor = executor;
        this.subscription = commands.subscribe(this::route,
                e -> log.log(System.Logger.Level.ERROR, "Localization command stream failed", e));
    }

    private void route(LocalizationsCmd<?, ?> cmd) {
        var videoUuid = cmd.getValue().getUuid();
        executor.execute(() -> lookup.apply(videoUuid).ifPresentOrElse(
                target -> apply(target, cmd),
                () -> log.log(System.Logger.Level.DEBUG, () -> "No video " + videoUuid + " for " + cmd.getName())));
    }

    private void apply(LocalizationTarget target, LocalizationsCmd<?, ?> cmd) {
        try {
            switch (cmd) {
                case AddLocalizationsCmd c -> target.add(nn(c.getValue().getLocalizations())
                        .stream()
                        .map(LocalizationRecord::fromRemote)
                        .flatMap(Optional::stream)
                        .toList());
                case UpdateLocalizationsCmd c -> target.update(nn(c.getValue().getLocalizations()));
                case RemoveLocalizationsCmd c -> target.remove(nn(c.getValue().getLocalizations()));
                case SelectLocalizationsCmd c -> target.select(nn(c.getValue().getLocalizations()));
                case ClearLocalizationsCmd c -> target.clear();
                default -> log.log(System.Logger.Level.WARNING, () -> "Unhandled command " + cmd.getName());
            }
        }
        catch (RuntimeException e) {
            log.log(System.Logger.Level.ERROR, "Failed to apply " + cmd.getName(), e);
        }
    }

    private static <T> List<T> nn(List<T> list) {
        return list == null ? List.of() : list;
    }

    @Override
    public void close() {
        subscription.dispose();
    }
}

package org.mbari.jsharktopoda.localization;

import org.mbari.vcr4j.remote.control.RVideoIO;
import org.mbari.vcr4j.remote.control.commands.RCommand;
import org.mbari.vcr4j.remote.control.commands.localization.AddLocalizationsCmd;
import org.mbari.vcr4j.remote.control.commands.localization.RemoveLocalizationsCmd;
import org.mbari.vcr4j.remote.control.commands.localization.SelectLocalizationsCmd;
import org.mbari.vcr4j.remote.control.commands.localization.UpdateLocalizationsCmd;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Tells the remote app about localization changes made in this app. Sends happen on a
 * background thread. If no remote app has connected the message is dropped.
 */
public class RemoteNotifier {

    @FunctionalInterface
    public interface Sender {
        /** @return false if there is no connection to send on */
        boolean send(RCommand<?, ?> command);
    }

    private static final System.Logger log = System.getLogger(RemoteNotifier.class.getName());

    private final Sender sender;
    private final Executor executor;

    public RemoteNotifier(Sender sender, Executor executor) {
        this.sender = sender;
        this.executor = executor;
    }

    /** @param connection the connection opened by the remote app's `connect` command, if any */
    public static RemoteNotifier forConnection(Supplier<Optional<RVideoIO>> connection) {
        var executor = Executors.newSingleThreadExecutor(r -> {
            var t = new Thread(r, "remote-notifier");
            t.setDaemon(true);
            return t;
        });
        Sender sender = cmd -> connection.get()
                .map(io -> {
                    io.send(cmd);
                    return true;
                })
                .orElse(false);
        return new RemoteNotifier(sender, executor);
    }

    public void added(UUID videoUuid, List<LocalizationRecord> records) {
        if (!records.isEmpty()) {
            send(new AddLocalizationsCmd(videoUuid, records.stream().map(LocalizationRecord::toRemote).toList()));
        }
    }

    public void updated(UUID videoUuid, List<LocalizationRecord> records) {
        if (!records.isEmpty()) {
            send(new UpdateLocalizationsCmd(videoUuid, records.stream().map(LocalizationRecord::toRemote).toList()));
        }
    }

    public void removed(UUID videoUuid, List<UUID> localizationUuids) {
        if (!localizationUuids.isEmpty()) {
            send(new RemoveLocalizationsCmd(videoUuid, List.copyOf(localizationUuids)));
        }
    }

    /** An empty list is sent: it tells the remote app the selection was cleared. */
    public void selected(UUID videoUuid, List<UUID> localizationUuids) {
        send(new SelectLocalizationsCmd(videoUuid, List.copyOf(localizationUuids)));
    }

    private void send(RCommand<?, ?> cmd) {
        executor.execute(() -> {
            try {
                if (!sender.send(cmd)) {
                    log.log(System.Logger.Level.WARNING,
                            () -> "No remote app connected; dropping " + cmd.getName());
                }
            }
            catch (RuntimeException e) {
                log.log(System.Logger.Level.WARNING, "Failed to send " + cmd.getName(), e);
            }
        });
    }
}

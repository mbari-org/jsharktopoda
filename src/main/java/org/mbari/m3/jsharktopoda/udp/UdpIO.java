package org.mbari.m3.jsharktopoda.udp;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.time.Duration;

/**
 * @author Brian Schlining
 * @since 2017-12-05T13:02:00
 */
public class UdpIO {
    private final int port;
    private DatagramSocket server;
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final Thread receiverThread;
    private final Subject<GenericCommand> commandSubject;
    private final Subject<GenericResponse> responseSubject;
    private volatile boolean ok = true;
    private final Gson gson = newGson();

    public UdpIO(int port) {
        this.port = port;
        PublishSubject<GenericCommand> s1 = PublishSubject.create();
        commandSubject = s1.toSerialized();

        // TODO handle connect command for framecapture. Configure socket to respond

        PublishSubject<GenericResponse> s2 = PublishSubject.create();
        responseSubject = s2.toSerialized();
        responseSubject.subscribe(this::doResponse);

        receiverThread = buildReceiverThread();
        receiverThread.setDaemon(true);
        receiverThread.start();
    }

    private void doResponse(GenericResponse response) {
        try {
            DatagramSocket s = getServer();
            byte[] b = gson.toJson(response).getBytes();
            DatagramPacket packet = new DatagramPacket(b, b.length);
            s.send(packet);
        }
        catch (Exception e) {
            log.error("UDP response failed", e);
        }
    }

    private Thread buildReceiverThread() {
        return new Thread(() -> {
            byte[] buffer = new byte[4096];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            while(ok) {
                try {
                    getServer().receive(packet);
                    String msg = new String(packet.getData(), 0, packet.getLength());
                    log.debug("GOT MSG: " + msg);
                    GenericCommand r = gson.fromJson(msg, GenericCommand.class);
                    commandSubject.onNext(r);
                }
                catch (Exception e) {
                    log.info("Error while reading UDP datagram", e);
                    if (!server.isClosed()) {
                        server.close();
                    }
                    if (server != null) {
                        server = null;
                    }
                }

            }
            if (server != null) {
                server.close();
            }
            log.info("Shutting down UDP server");

        });
    }



    private DatagramSocket getServer() throws SocketException {
        if (server == null || server.isClosed()) {
            server = new DatagramSocket(port);
        }
        return server;
    }

    public static Gson newGson() {
        return new GsonBuilder().setPrettyPrinting()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .setDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
                .registerTypeAdapter(Duration.class, new DurationConverter())
                .create();

    }

    public Subject<GenericResponse> getResponseSubject() {
        return responseSubject;
    }

    public Subject<GenericCommand> getCommandSubject() {
        return commandSubject;
    }
}

package uz.usta.platform.chat.internal.application;

import java.util.UUID;

/** Shu instansiyadagi foydalanuvchining ochiq WebSocket sessiyalarini uzadi. */
public interface SessionCloser {

    void closeSessionsOf(UUID userId);
}

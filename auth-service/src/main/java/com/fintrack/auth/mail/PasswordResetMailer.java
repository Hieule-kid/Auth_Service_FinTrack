package com.fintrack.auth.mail;

/**
 * Delivers password-reset links to users.
 *
 * <p>Implementations must never throw into the caller and should be asynchronous: the
 * forgot-password endpoint must respond identically (and in similar time) whether or not the
 * e-mail address belongs to an account, otherwise it becomes an account-enumeration oracle.
 */
public interface PasswordResetMailer {

    /**
     * @param toEmail   recipient address
     * @param fullName  recipient display name, may be {@code null}
     * @param resetLink absolute URL containing the raw reset token
     * @param ttlMinutes how long the link remains valid, for display in the message
     */
    void sendResetLink(String toEmail, String fullName, String resetLink, long ttlMinutes);
}

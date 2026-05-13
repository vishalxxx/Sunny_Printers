package utils;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import model.EmailSettings;
import repository.EmailSettingsRepository;

import java.util.Properties;

public class EmailUtil {

    public static void sendWelcomeEmail(String recipientEmail, String clientName) {
        new Thread(() -> {
            try {
                EmailSettingsRepository repo = new EmailSettingsRepository();
                EmailSettings settings = repo.load();

                String smtpHost = settings.getSmtpHost();
                String smtpPort = settings.getSmtpPort();
                String senderEmail = settings.getSenderEmail();
                String senderPassword = settings.getSenderPassword();

                if (senderEmail == null || senderEmail.isBlank() || senderPassword == null || senderPassword.isBlank()) {
                    System.err.println("❌ Email settings not completely configured, skipping welcome email.");
                    return;
                }

                Properties props = new Properties();
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.host", smtpHost);
                props.put("mail.smtp.port", smtpPort);

                Session session = Session.getInstance(props, new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(senderEmail, senderPassword);
                    }
                });

                Message message = new MimeMessage(session);
                message.setFrom(new InternetAddress(senderEmail, "Sunny Printers"));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail));
                message.setSubject("Welcome to Sunny Printers!");

                String htmlContent = "<h3>Dear " + clientName + ",</h3>"
                        + "<p>Welcome to <b>Sunny Printers</b>! We are thrilled to have you as a new client.</p>"
                        + "<p>If you have any questions or printing requests, feel free to contact us.</p>"
                        + "<br><p>Best Regards,</p>"
                        + "<p><b>Sunny Printers Team</b></p>";

                message.setContent(htmlContent, "text/html; charset=utf-8");

                Transport.send(message);
                System.out.println("✅ Welcome email sent successfully to " + recipientEmail);

            } catch (Exception e) {
                System.err.println("❌ Failed to send welcome email to " + recipientEmail + ": " + e.getMessage());
            }
        }).start();
    }
}

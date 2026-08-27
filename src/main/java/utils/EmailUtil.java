package utils;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.Multipart;
import model.EmailSettings;
import repository.EmailSettingsRepository;

import java.util.Properties;
import java.io.File;

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

    public static void sendInvoiceEmail(String recipientEmail, String clientName, String invoiceNo, File pdfFile) {
        sendInvoiceEmail(recipientEmail, clientName, invoiceNo, pdfFile, null, null);
    }

    public static void sendInvoiceEmail(String recipientEmail, String clientName, String invoiceNo, File pdfFile, Runnable onSuccess, java.util.function.Consumer<String> onFailure) {
        new Thread(() -> {
            try {
                EmailSettingsRepository repo = new EmailSettingsRepository();
                EmailSettings settings = repo.load();

                String smtpHost = settings.getSmtpHost();
                String smtpPort = settings.getSmtpPort();
                String senderEmail = settings.getSenderEmail();
                String senderPassword = settings.getSenderPassword();

                if (senderEmail == null || senderEmail.isBlank() || senderPassword == null || senderPassword.isBlank()) {
                    String msg = "Email credentials (sender email/password) not configured in Settings.";
                    System.err.println("❌ " + msg);
                    if (onFailure != null) {
                        onFailure.accept(msg);
                    }
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
                message.setSubject("Invoice " + invoiceNo + " from Sunny Printers");

                MimeBodyPart messageBodyPart = new MimeBodyPart();
                String htmlContent = "<h3>Dear " + clientName + ",</h3>"
                        + "<p>Please find attached the invoice <b>" + invoiceNo + "</b> for your reference.</p>"
                        + "<p>If you have any questions, feel free to contact us.</p>"
                        + "<br><p>Best Regards,</p>"
                        + "<p><b>Sunny Printers Team</b></p>";
                messageBodyPart.setContent(htmlContent, "text/html; charset=utf-8");

                Multipart multipart = new MimeMultipart();
                multipart.addBodyPart(messageBodyPart);

                if (pdfFile != null && pdfFile.exists()) {
                    MimeBodyPart attachmentPart = new MimeBodyPart();
                    attachmentPart.attachFile(pdfFile);
                    attachmentPart.setFileName(pdfFile.getName());
                    multipart.addBodyPart(attachmentPart);
                }

                message.setContent(multipart);

                Transport.send(message);
                System.out.println("✅ Invoice email sent successfully to " + recipientEmail);
                if (onSuccess != null) {
                    onSuccess.run();
                }

            } catch (Exception e) {
                System.err.println("❌ Failed to send invoice email to " + recipientEmail + ": " + e.getMessage());
                e.printStackTrace();
                if (onFailure != null) {
                    onFailure.accept(e.getMessage());
                }
            }
        }).start();
    }
}

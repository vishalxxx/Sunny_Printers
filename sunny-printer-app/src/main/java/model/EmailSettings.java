package model;

public class EmailSettings {
    private String smtpHost;
    private String smtpPort;
    private String senderEmail;
    private String senderPassword;

    public String getSmtpHost() { return smtpHost; }
    public void setSmtpHost(String smtpHost) { this.smtpHost = smtpHost; }

    public String getSmtpPort() { return smtpPort; }
    public void setSmtpPort(String smtpPort) { this.smtpPort = smtpPort; }

    public String getSenderEmail() { return senderEmail; }
    public void setSenderEmail(String senderEmail) { this.senderEmail = senderEmail; }

    public String getSenderPassword() { return senderPassword; }
    public void setSenderPassword(String senderPassword) { this.senderPassword = senderPassword; }
}

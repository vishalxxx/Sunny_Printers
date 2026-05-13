package repository;

import model.EmailSettings;
import utils.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class EmailSettingsRepository {

    public EmailSettings load() throws Exception {
        EmailSettings s = new EmailSettings();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM email_settings WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {
             
            if (rs.next()) {
                s.setSmtpHost(rs.getString("smtp_host"));
                s.setSmtpPort(rs.getString("smtp_port"));
                s.setSenderEmail(rs.getString("sender_email"));
                s.setSenderPassword(rs.getString("sender_password"));
            }
        }
        return s;
    }

    public void save(EmailSettings s) throws Exception {
        String sql = "UPDATE email_settings SET smtp_host = ?, smtp_port = ?, sender_email = ?, sender_password = ?, updated_at = CURRENT_TIMESTAMP WHERE id = 1";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
             
            ps.setString(1, s.getSmtpHost());
            ps.setString(2, s.getSmtpPort());
            ps.setString(3, s.getSenderEmail());
            ps.setString(4, s.getSenderPassword());
            
            ps.executeUpdate();
        }
    }
}

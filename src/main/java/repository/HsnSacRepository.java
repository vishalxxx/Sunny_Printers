package repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import model.HsnSacInfo;

public class HsnSacRepository {

    public HsnSacInfo findBestMatch(Connection con, String itemType, String description) {
        String sql = """
                SELECT hsn_sac, gst_rate, unit_default
                FROM hsn_sac_master
                WHERE IFNULL(is_deleted, 0) = 0
                  AND IFNULL(is_active, 1) = 1
                  AND item_type = ?
                  AND (
                        keyword IS NULL
                        OR keyword = ''
                        OR instr(lower(?), lower(keyword)) > 0
                  )
                ORDER BY
                  CASE WHEN keyword IS NULL OR keyword = '' THEN 1 ELSE 0 END,
                  length(COALESCE(keyword,'')) DESC
                LIMIT 1
                """;

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, itemType != null ? itemType.trim() : "");
            ps.setString(2, description != null ? description : "");

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new HsnSacInfo(
                            rs.getString("hsn_sac"),
                            rs.getDouble("gst_rate"),
                            rs.getString("unit_default")
                    );
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to lookup HSN/SAC mapping", e);
        }

        return null;
    }

    public List<HsnSacInfo> listActiveByType(Connection con, String itemType) {
        String sql = """
                SELECT hsn_sac, gst_rate, unit_default
                FROM hsn_sac_master
                WHERE IFNULL(is_deleted, 0) = 0
                  AND IFNULL(is_active, 1) = 1
                  AND item_type = ?
                ORDER BY hsn_sac
                """;

        List<HsnSacInfo> out = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, itemType != null ? itemType.trim() : "");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new HsnSacInfo(
                            rs.getString("hsn_sac"),
                            rs.getDouble("gst_rate"),
                            rs.getString("unit_default")
                    ));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load HSN/SAC list", e);
        }
        return out;
    }

    public List<HsnSacInfo> listAllActiveHsnSac(Connection con) {
        String sql = """
                SELECT DISTINCT hsn_sac, gst_rate, unit_default
                FROM hsn_sac_master
                WHERE IFNULL(is_deleted, 0) = 0
                  AND IFNULL(is_active, 1) = 1
                  AND hsn_sac IS NOT NULL
                  AND trim(hsn_sac) != ''
                ORDER BY hsn_sac
                """;

        List<HsnSacInfo> out = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new HsnSacInfo(
                        rs.getString("hsn_sac"),
                        rs.getDouble("gst_rate"),
                        rs.getString("unit_default")
                ));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load active HSN/SAC catalog", e);
        }
        return out;
    }

    public HsnSacInfo findBestMatchByNameOrDesc(Connection con, String name) {
        String sql = """
                SELECT hsn_sac, gst_rate, unit_default
                FROM hsn_sac_master
                WHERE IFNULL(is_deleted, 0) = 0
                  AND IFNULL(is_active, 1) = 1
                  AND (
                     lower(trim(item_name)) = lower(trim(?))
                     OR lower(trim(keyword)) = lower(trim(?))
                     OR lower(trim(description)) = lower(trim(?))
                  )
                LIMIT 1
                """;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, name);
            ps.setString(3, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new HsnSacInfo(
                            rs.getString("hsn_sac"),
                            rs.getDouble("gst_rate"),
                            rs.getString("unit_default")
                    );
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to lookup HSN/SAC by name", e);
        }
        return null;
    }
}

package service;

import java.sql.Connection;
import java.util.List;

import model.HsnSacInfo;
import model.JobItem;
import repository.HsnSacRepository;
import utils.DBConnection;

public class HsnSacService {
    private final HsnSacRepository repo = new HsnSacRepository();

    public HsnSacInfo lookup(JobItem item) {
        if (item == null) {
            return null;
        }
        String type = item.getType() != null ? item.getType().trim() : "";
        String desc = item.getDescription();

        try (Connection con = DBConnection.getConnection()) {
            return repo.findBestMatch(con, type, desc);
        } catch (Exception e) {
            throw new RuntimeException("Failed to lookup HSN/SAC", e);
        }
    }

    public List<HsnSacInfo> listActiveByType(String itemType) {
        try (Connection con = DBConnection.getConnection()) {
            return repo.listActiveByType(con, itemType);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load HSN/SAC list", e);
        }
    }
}


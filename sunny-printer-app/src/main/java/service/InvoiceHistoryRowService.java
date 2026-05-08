package service;

import model.Client;
import model.Invoice;
import model.InvoiceHistoryRow;
import repository.InvoiceHistoryRepo;

import java.util.List;

public class InvoiceHistoryRowService {

    private final InvoiceHistoryRepo repo = new InvoiceHistoryRepo();

    public void saveHistory(
            String invoiceNo,
            int clientId,
            String clientName,
            String invoiceDate,
            double amount,
            String type,
            String status,
            String filePath
    ) {
        repo.insertHistory(invoiceNo, clientId, clientName, invoiceDate, amount, type, status, filePath);
    }

    public void saveHistory(Invoice invoice, Client client, String type, String status, String filePath) {

        saveHistory(
                invoice.getInvoiceNo(),
                client.getId(),
                invoice.getClientName(),
                invoice.getInvoiceDate().toString(),
                invoice.getGrandTotal(),
                type,
                status,
                filePath
        );
    }
 
    public void saveHistory(Invoice invoice, String type, String status, String filePath) {

        saveHistory(
                invoice.getInvoiceNo(),
                invoice.getClientId(),    // ✅ must exist in Invoice model
                invoice.getClientName(),
                invoice.getInvoiceDate().toString(),
                invoice.getGrandTotal(),
                type,
                status,
                filePath
        );
    }

    
    public List<InvoiceHistoryRow> getRecentHistory(int limit) {
        return repo.getRecentHistory(limit);
    }
}

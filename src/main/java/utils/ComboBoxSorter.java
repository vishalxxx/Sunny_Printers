package utils;

import java.util.Comparator;
import java.util.List;
import model.Client;
import model.InvoiceMaster;
import model.Supplier;

public class ComboBoxSorter {

    public static void sortStrings(List<String> items) {
        if (items == null || items.size() <= 1) return;
        String first = items.get(0);
        boolean hasPlaceholder = first != null && (first.startsWith("Select") || first.startsWith("All") || first.startsWith("Search") || first.contains(" / ") || first.startsWith("With CTP"));
        if (hasPlaceholder) {
            List<String> sub = items.subList(1, items.size());
            sub.sort(String.CASE_INSENSITIVE_ORDER);
        } else {
            items.sort(String.CASE_INSENSITIVE_ORDER);
        }
    }

    public static void sortClients(List<Client> clients) {
        if (clients == null || clients.size() <= 1) return;
        clients.sort((c1, c2) -> {
            String name1 = c1.getBusinessName() != null ? c1.getBusinessName() : "";
            String name2 = c2.getBusinessName() != null ? c2.getBusinessName() : "";
            if (name1.isEmpty() && name2.isEmpty()) {
                name1 = c1.toString() != null ? c1.toString() : "";
                name2 = c2.toString() != null ? c2.toString() : "";
            }
            return String.CASE_INSENSITIVE_ORDER.compare(name1, name2);
        });
    }

    public static void sortInvoices(List<InvoiceMaster> invoices) {
        if (invoices == null || invoices.size() <= 1) return;
        invoices.sort((i1, i2) -> {
            String no1 = i1.getInvoiceNo() != null ? i1.getInvoiceNo() : "";
            String no2 = i2.getInvoiceNo() != null ? i2.getInvoiceNo() : "";
            return String.CASE_INSENSITIVE_ORDER.compare(no1, no2);
        });
    }

    public static void sortSuppliers(List<Supplier> suppliers) {
        if (suppliers == null || suppliers.size() <= 1) return;
        Supplier first = suppliers.get(0);
        boolean hasPlaceholder = first != null && (first.getUuid() == null || first.getUuid().isBlank() || (first.getbusinessName() != null && first.getbusinessName().startsWith("Select")));
        if (hasPlaceholder) {
            List<Supplier> sub = suppliers.subList(1, suppliers.size());
            sub.sort((s1, s2) -> {
                String name1 = s1.getbusinessName() != null ? s1.getbusinessName() : "";
                String name2 = s2.getbusinessName() != null ? s2.getbusinessName() : "";
                return String.CASE_INSENSITIVE_ORDER.compare(name1, name2);
            });
        } else {
            suppliers.sort((s1, s2) -> {
                String name1 = s1.getbusinessName() != null ? s1.getbusinessName() : "";
                String name2 = s2.getbusinessName() != null ? s2.getbusinessName() : "";
                return String.CASE_INSENSITIVE_ORDER.compare(name1, name2);
            });
        }
    }
}

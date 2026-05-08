import sqlite3
from datetime import datetime, timedelta

def populate_test_data():
    conn = sqlite3.connect('database/sunnyprinters.db')
    cursor = conn.cursor()

    # Target Client ID: 1
    client_id = 1
    
    # 1. Clear existing test data for this client if any (optional, but safer for testing)
    # cursor.execute("DELETE FROM invoice_master WHERE invoice_no LIKE 'INV-T%'")
    # cursor.execute("DELETE FROM payments WHERE client_id = ?", (client_id,))

    test_invoices = [
        ('INV-T001', client_id, 'Techno Solutions', '2026-04-10', 5000.0, 'SENT', 'PAID'),
        ('INV-T002', client_id, 'Techno Solutions', '2026-04-12', 8500.0, 'SENT', 'PARTIAL'),
        ('INV-T003', client_id, 'Techno Solutions', '2026-04-14', 12000.0, 'SENT', 'UNPAID'),
        ('INV-T004', client_id, 'Techno Solutions', '2026-04-15', 3000.0, 'DRAFT', 'UNPAID')
    ]

    for inv in test_invoices:
        cursor.execute("""
            INSERT INTO invoice_master (invoice_no, client_id, client_name, invoice_date, amount, status, payment_status, is_void)
            VALUES (?, ?, ?, ?, ?, ?, ?, 0)
        """, inv)

    test_payments = [
        (client_id, 2500.0, '2026-04-15', 'CASH', 'Payment')
    ]

    for pmt in test_payments:
        cursor.execute("""
            INSERT INTO payments (client_id, amount, payment_date, method, type)
            VALUES (?, ?, ?, ?, ?)
        """, pmt)

    conn.commit()
    conn.close()
    print("Test data injected successfully for Client ID 1.")

if __name__ == "__main__":
    populate_test_data()

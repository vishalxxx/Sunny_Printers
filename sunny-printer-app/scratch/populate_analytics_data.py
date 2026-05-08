import sqlite3
import random
from datetime import datetime, timedelta

def populate_analytics():
    conn = sqlite3.connect('database/sunnyprinters.db')
    cursor = conn.cursor()
    
    # 1. Clear existing jobs/invoices/payments for a clean demo if needed, 
    # but here we just add new clients or update existing ones.
    
    # Let's create specific personas
    personas = [
        ("Vanguard Industries", "Alex Vance", "Risk Case (High Bal/LTV)", 100000, 95000),
        ("Lumina Media", "Sarah Light", "Platinum VIP (High LTV, 0 Bal)", 250000, 0),
        ("Echo Soft", "Marcus Cole", "Advancing (Credit Bal)", 50000, -5000),
        ("Growth Labs", "Dr. Green", "High Potential (Active, Low LTV)", 15000, 0),
        ("Old Guard Prints", "Arthur Stone", "Inactive High Value", 120000, 10), # Low activity
        ("Busy Bees Ltd", "Maya Honey", "Good Standing (Active)", 45000, 0)
    ]
    
    for biz, contact, note, target_ltv, target_bal in personas:
        # Create Client
        cursor.execute("INSERT INTO clients (business_name, client_name, email, phone) VALUES (?, ?, ?, ?)",
                       (biz, contact, f"{biz.lower().replace(' ','')}@example.com", "9876543210"))
        client_id = cursor.lastrowid
        print(f"Created {biz} with ID {client_id}")
        
        # Populate Invoices to reach target_ltv
        # We'll create one big invoice or multiple.
        num_inv = random.randint(3, 8)
        avg_inv = target_ltv / num_inv
        for i in range(num_inv):
            inv_date = (datetime.now() - timedelta(days=random.randint(5, 60))).strftime('%Y-%m-%d')
            cursor.execute("INSERT INTO invoice_master (client_id, invoice_date, amount, status, is_void) VALUES (?, ?, ?, 'Final', 0)",
                           (client_id, inv_date, avg_inv))
        
        # Populate Payments to reach target_bal
        # bal = LTV - Payments -> Payments = LTV - Bal
        target_paid = target_ltv - target_bal
        if target_paid > 0:
            cursor.execute("INSERT INTO payments (client_id, amount, payment_date, method) VALUES (?, ?, ?, 'Bank Transfer')",
                           (client_id, target_paid, datetime.now().strftime('%Y-%m-%d')))
            
        # Add some very recent activity if it's a "High Potential" or "Active" case
        if "Active" in note or "High Potential" in note:
            for _ in range(10):
                cursor.execute("INSERT INTO invoice_master (client_id, invoice_date, amount, status, is_void) VALUES (?, ?, ?, 'Final', 0)",
                               (client_id, datetime.now().strftime('%Y-%m-%d'), random.randint(100, 500)))

    conn.commit()
    conn.close()
    print("Database populated for analytics demo.")

if __name__ == "__main__":
    populate_analytics()

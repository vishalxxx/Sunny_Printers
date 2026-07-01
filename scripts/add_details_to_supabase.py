import sqlite3
import uuid
import requests
import json
from datetime import datetime

DB_PATH = 'database/sunnyprinters.db'

def get_supabase_settings():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    cur = conn.cursor()
    cur.execute("SELECT supabase_url, anon_key FROM supabase_settings LIMIT 1")
    row = cur.fetchone()
    conn.close()
    if row and row['supabase_url'] and row['anon_key']:
        return row['supabase_url'], row['anon_key']
    return None, None

def populate_missing_uuids():
    conn = sqlite3.connect(DB_PATH)
    cur = conn.cursor()
    
    tables = ['company_details', 'bank_details', 'hsn_sac_master']
    updated_counts = {}
    
    for table in tables:
        cur.execute(f"SELECT id FROM {table} WHERE uuid IS NULL OR uuid = ''")
        rows = cur.fetchall()
        count = 0
        for r in rows:
            row_id = r[0]
            new_uuid = str(uuid.uuid4())
            cur.execute(f"UPDATE {table} SET uuid = ?, sync_status = 'PENDING' WHERE id = ?", (new_uuid, row_id))
            count += 1
        updated_counts[table] = count
    
    conn.commit()
    conn.close()
    return updated_counts

def sync_table_to_supabase(url, anon_key, table_name):
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    cur = conn.cursor()
    
    # Select all rows that are pending sync
    cur.execute(f"SELECT * FROM {table_name}")
    rows = cur.fetchall()
    
    headers = {
        "apikey": anon_key,
        "Authorization": f"Bearer {anon_key}",
        "Content-Type": "application/json",
        "Prefer": "resolution=merge-duplicates,return=minimal"
    }
    
    endpoint = f"{url}/rest/v1/{table_name}"
    
    success_count = 0
    fail_count = 0
    
    for r in rows:
        row_dict = dict(r)
        
        # Remove sqlite specific 'id' (since we use GENERATED ALWAYS AS IDENTITY on supabase and identify by uuid)
        # Also clean up values for JSON payload
        row_id = row_dict.pop('id', None)
        record_uuid = row_dict.get('uuid')
        
        if not record_uuid:
            continue
            
        # SQLite uses 0/1 integers for booleans, let's map them to JSON booleans
        for col in ['is_deleted', 'is_active', 'is_default', 'is_favorite']:
            if col in row_dict and row_dict[col] is not None:
                row_dict[col] = bool(row_dict[col])
                
        # Send post request
        res = requests.post(f"{endpoint}?on_conflict=uuid", json=[row_dict], headers=headers)
        
        if 200 <= res.status_code < 300:
            # Mark as synced locally
            cur.execute(
                f"UPDATE {table_name} SET sync_status = 'SYNCED', synced_at = ? WHERE uuid = ?",
                (datetime.now().strftime('%Y-%m-%d %H:%M:%S'), record_uuid)
            )
            success_count += 1
        else:
            print(f"Failed to sync row {record_uuid} in {table_name}: HTTP {res.status_code} - {res.text}")
            fail_count += 1
            
    conn.commit()
    conn.close()
    return success_count, fail_count

def main():
    url, anon_key = get_supabase_settings()
    if not url or not anon_key:
        print("Error: Supabase settings not configured in local database.")
        return
        
    print(f"Found Supabase URL: {url}")
    
    # 1. Populate UUIDs
    print("Populating missing UUIDs in SQLite...")
    uuid_stats = populate_missing_uuids()
    for table, count in uuid_stats.items():
        print(f"  {table}: Generated {count} UUIDs")
        
    # 2. Sync Tables
    tables = ['company_details', 'bank_details', 'hsn_sac_master']
    for table in tables:
        print(f"Syncing {table} to Supabase...")
        try:
            success, fails = sync_table_to_supabase(url, anon_key, table)
            print(f"  {table}: Successfully synced {success} records, {fails} failed.")
        except Exception as e:
            print(f"  Error syncing {table}: {e}")

if __name__ == "__main__":
    main()

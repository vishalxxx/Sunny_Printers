-- Migration to remove 'supplier_name' from ctp_items and paper_items tables

-- First, ensure that supplier_name is actually dropped from ctp_items
ALTER TABLE ctp_items DROP COLUMN IF EXISTS supplier_name;

-- Next, ensure that supplier_name is dropped from paper_items
ALTER TABLE paper_items DROP COLUMN IF EXISTS supplier_name;

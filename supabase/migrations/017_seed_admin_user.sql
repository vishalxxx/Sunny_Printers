-- Seed default local Admin user to Supabase to satisfy foreign key constraints
-- Run in Supabase SQL Editor

SET search_path TO public;

DO $$
BEGIN
    -- Ensure the uuid column exists in Supabase users table (it may have been added manually)
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema='public' AND table_name='users' AND column_name='uuid'
    ) THEN
        -- Insert the local SQLite default admin user
        INSERT INTO public.users (uuid, username, password, role)
        SELECT '00000000-0000-0000-0000-00000000000a', 'Admin', 'admin', 'ADMIN'
        WHERE NOT EXISTS (
            SELECT 1 FROM public.users WHERE uuid = '00000000-0000-0000-0000-00000000000a'
        );
    END IF;
END $$;

NOTIFY pgrst, 'reload schema';
